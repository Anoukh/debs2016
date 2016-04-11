package org.wso2.siddhi.debs2016.Processors;

import com.google.common.collect.Multimap;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.wso2.siddhi.debs2016.comment.CommentStore;
import org.wso2.siddhi.debs2016.graph.Graph;
import org.wso2.siddhi.debs2016.util.Constants;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Created by bhagya on 3/30/16.
 */
public class Q2EventManager {


    private Disruptor<DEBSEvent> dataReadDisruptor;
    private Disruptor<KLargestEvent> outputDisruptor;
    private RingBuffer dataReadBuffer;
    private RingBuffer outputBuffer;
    private String ts;
    private long duration=  3600000*24;
    public Graph friendshipGraph ;
    private CommentStore commentStore ;
    private int k = 2;
    long timeDifference = 0; //This is the time difference for this time window.
    long startTime = 0;
    private Date startDateTime;
    static int bufferSize = 8192;
    private long sequenceNumber;
    private OutputProcessor outputProcessor ;

    /**
     * The constructor
     *
     */
    public Q2EventManager(){
        List<Attribute> attributeList = new ArrayList<Attribute>();
        outputProcessor = new OutputProcessor();
        startDateTime = new Date();
        startTime = startDateTime.getTime();
        SimpleDateFormat ft = new SimpleDateFormat ("yyyy-MM-dd.hh:mm:ss-a-zzz");
        System.out.println("Started experiment at : " + startTime + "--" + ft.format(startDateTime));
    }

    /**
     * Gets the reference to data reader distruptor
     *
     * @return the data reader distruptor
     */
    public Disruptor<DEBSEvent> getDataReadDisruptor() {
        return dataReadDisruptor;
    }
    /**
     *
     * Starts the distruptor + other threads
     *
     */
    public void run() {

        dataReadDisruptor = new Disruptor<DEBSEvent>(new com.lmax.disruptor.EventFactory<DEBSEvent>() {

            @Override
            public DEBSEvent newInstance() {
                return new DEBSEvent();
            }
        }, bufferSize, Executors.newFixedThreadPool(4), ProducerType.SINGLE, new SleepingWaitStrategy());


        outputDisruptor = new Disruptor<KLargestEvent>(new com.lmax.disruptor.EventFactory<KLargestEvent>() {

            @Override
            public KLargestEvent newInstance() {
                return new  KLargestEvent();
            }
        }, bufferSize, Executors.newFixedThreadPool(1), ProducerType.MULTI, new SleepingWaitStrategy());


        DEBSEventHandler debsEventHandler1 = new DEBSEventHandler(0);
        DEBSEventHandler debsEventHandler2 = new DEBSEventHandler(1);
        DEBSEventHandler debsEventHandler3 = new DEBSEventHandler(2);
        DEBSEventHandler debsEventHandler4 = new DEBSEventHandler(3);

        KLargestEventHandler kLargestEventHandler = new KLargestEventHandler();
        dataReadDisruptor.handleEventsWith(debsEventHandler1);
        dataReadDisruptor.handleEventsWith(debsEventHandler2);
        dataReadDisruptor.handleEventsWith(debsEventHandler3);
        dataReadDisruptor.handleEventsWith(debsEventHandler4);
        outputDisruptor.handleEventsWith(kLargestEventHandler);

        dataReadBuffer = dataReadDisruptor.start();
        outputBuffer =  outputDisruptor.start();
        outputProcessor.start();
    }

    /**
     * Gets the reference to next DebsEvent from the ring butter
     *
     * @return the DebsEvent
     */
    public DEBSEvent getNextDebsEvent()
    {
        sequenceNumber = dataReadBuffer.next();
        return dataReadDisruptor.get(sequenceNumber);
    }


    /**
     * Publish the new event
     *
     */
    public void publish()
    {
        dataReadBuffer.publish(sequenceNumber);
    }




    /**
     *
     * The debs event handler
     *
     */
    private class DEBSEventHandler implements EventHandler<DEBSEvent>{
        public Graph friendshipGraph ;
        private CommentStore commentStore ;
        private int handlerId;
        private int myHandlerID;
        private long count = 0;
        private long numberOfOutputs = 0;
        private long latency = 0;
        private long startiij_timestamp;
        private long endiij_timestamp;

        /**
         * The constructor
         *
         * @param myHandlerID the handler id
         */
        public DEBSEventHandler(int myHandlerID){
            this.myHandlerID = myHandlerID;
            friendshipGraph = new Graph();
            commentStore = new CommentStore(duration, friendshipGraph, k);
        }
        @Override
        public void onEvent(DEBSEvent debsEvent, long l, boolean b) throws Exception {
            try{

                Object[] objects = debsEvent.getObjectArray();
                long ts = (Long) objects[1];
                commentStore.cleanCommentStore(ts);
                count++;
                if(myHandlerID == debsEvent.getHandlerId()|| debsEvent.getHandlerId()==-1) {

                    int streamType = (Integer) objects[8];
                    switch (streamType) {
                        case Constants.COMMENTS:
                            long comment_id = (Long) objects[3];
                            String comment = (String) objects[4];
                            commentStore.registerComment(comment_id, ts, comment, false);
                            break;
                        case Constants.FRIENDSHIPS:
                            if (ts == -2) {
                                count--;
                                showFinalStatistics(myHandlerID, count, latency);
                                commentStore.destroy();
                                break;
                            } else if (ts == -1) {
                                count--;
                                startiij_timestamp = (Long) debsEvent.getSystemArrivalTime();
                                break;
                            } else {
                                long user_id_1 = (Long) objects[2];
                                long friendship_user_id_2 = (Long) objects[3];
                                friendshipGraph.addEdge(user_id_1, friendship_user_id_2);
                                commentStore.handleNewFriendship(user_id_1, friendship_user_id_2);
                                break;
                            }
                        case Constants.LIKES:
                            long user_id_1 = (Long) objects[2];
                            long like_comment_id = (Long) objects[3];
                            commentStore.registerLike(like_comment_id, user_id_1);
                            break;
                    }

                }

                if (ts != -2 && ts != -1) {
                    Long endTime = commentStore.computeKLargestComments(" : ", false, false);
                    Multimap<Long, String> kLargestComments = commentStore.getTopKComments();
                    long sequenceNumber1 = outputBuffer.next();
                    KLargestEvent kLargestEvent =  outputDisruptor.get(sequenceNumber1);
                    kLargestEvent.setKLargestComment(kLargestComments);
                    kLargestEvent.setTimeStamp(ts);
                    kLargestEvent.setEventHandler(myHandlerID);
                    outputBuffer.publish(sequenceNumber1);

                    if (endTime != -1L) {
                        latency += (endTime - (Long) debsEvent.getSystemArrivalTime());
                        numberOfOutputs++;
                    }
                    endiij_timestamp = System.currentTimeMillis();
                }

            }catch (Exception e)
            {
                e.printStackTrace();
            }

        }
        /**
         *
         * Print the throughput etc
         *
         */
        private void showFinalStatistics(int handlerID, long count, long latency)
        {

            try {
                StringBuilder builder = new StringBuilder();
                StringBuilder builder1=new StringBuilder();
                BufferedWriter writer;
                File performance = new File("performance.txt");
                writer = new BufferedWriter(new FileWriter(performance, true));
                builder.setLength(0);

                timeDifference = endiij_timestamp - startiij_timestamp;

                Date dNow = new Date();
                SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd.hh:mm:ss-a-zzz");
                System.out.println("\n\n Query 2 has completed ..........\n\n");

                builder1.append("Handler ID = " + handlerID + " ");

                builder1.append("Event count = " + count + " ");


                String timeDifferenceString = String.format("%06d", timeDifference / 1000); //Convert time to seconds
                builder1.append("Total run time : " + timeDifferenceString + ",");

                builder.append(timeDifferenceString);
                builder.append(" ");

                builder1.append("Throughput (events/s) = " + Math.round((count * 1000.0) / timeDifference) + ",");

                builder1.append("Total Latency = " + latency + ",");

                builder1.append("Total Outputs = " + count + ",");
                if (count != 0) {
                    long averageLatency =  (latency / count);
                    String latencyString = String.format("%06d", averageLatency);
                    builder1.append("Average Latency = " + averageLatency);
                    builder.append(latencyString);
                }
                System.out.println(builder1.toString());
                writer.write(builder.toString());
                writer.close();
                System.out.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * The k largest event handler
     *
     */
    private class KLargestEventHandler implements EventHandler<KLargestEvent>{
        public Graph friendshipGraph ;
        private CommentStore commentStore ;
        private int handlerId;


        private long numberOfOutputs = 0;
        private long latency = 0;
        private int count1  = 0;
        private int count2 = 0;
        private int count3 = 0;
        private int count4 = 0;

        /**
         * The constructor
         *
         */
        public KLargestEventHandler(){

        }
        @Override
        public void onEvent(KLargestEvent KLargestEvent, long l, boolean b) throws Exception {
            try{
                int handlerID = KLargestEvent.getHandlerID();
                outputProcessor.add(KLargestEvent, handlerID);

            }catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}


