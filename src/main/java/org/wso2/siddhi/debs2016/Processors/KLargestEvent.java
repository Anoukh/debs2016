package org.wso2.siddhi.debs2016.Processors;

/**
 * Created by malithjayasinghe on 4/9/16.
 */
public class KLargestEvent {

    private String [] kLargestComment;
    private long timeStamp;
    private int handlerID;


    public void setKLargestComment(String [] kLargestComment)
    {
        this.kLargestComment = kLargestComment;
    }

    public void setTimeStamp(long timestamp)
    {
        this.timeStamp = timestamp;
    }

    /**
     *
     * Gets the k largest connected array
     *
     * @return the k largest connected array
     */
    public String[] getkLargestComment()
    {
        return kLargestComment;
    }

    /**
     * The time stamp of the event
     *
     * @return the event time
     */
    public long getTimeStamp()
    {
        return timeStamp;
    }

    public void setEventHandler(int handlerID)
    {
        this.handlerID = handlerID;
    }

    public int getHandlerID()
    {
        return handlerID;
    }

}