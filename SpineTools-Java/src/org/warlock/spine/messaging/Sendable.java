/*

 Copyright 2014 Health and Social Care Information Centre
 Solution Assurance <damian.murphy@hscic.gov.uk>

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.warlock.spine.messaging;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Calendar;
import org.warlock.spine.connection.ConnectionManager;
import org.warlock.spine.connection.SdsTransmissionDetails;
import org.warlock.spine.connection.ConditionalCompilationControls;
import org.warlock.spine.logging.SpineToolsLogger;

/**
 * Abstract representation of something that can be sent over Spine. To some extent this
 * leans toward ebXml as it handles expiry and the messaging contract properties that are
 * principally ebXml concepts.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public abstract class Sendable {

    // Types
    //
    public static final int UNDEFINED = 0;
    public static final int EBXML = 1;
    public static final int SOAP = 2;
    public static final int ACK = 3;

    protected int type = UNDEFINED;
    protected int retryCount = SdsTransmissionDetails.NOT_SET;
    protected int minRetryInterval = SdsTransmissionDetails.NOT_SET;
    protected int persistDuration = SdsTransmissionDetails.NOT_SET;
    protected String synchronousResponse = null;
    protected String resolvedUrl = null;
    protected String soapAction = null;
    protected String onTheWireRequest = null;

    protected Calendar started = Calendar.getInstance();
    protected Calendar lastTry = null;
    protected int tries = 0;

    /**
     * Called by the ConnectionManager's retry processor when an un-acknowledged
     * message is expired. Saves a copy of the message to the expired message
     * directory (as configured in the ConnectionManager), and calls any expiry
     * handler that is registered for use on messages of this type.
     */
    public void expire() {
        try {
            ConnectionManager c = ConnectionManager.getInstance();
            String dir = c.getExpiredMessageDirectory();
            if (dir != null) {
                StringBuilder filename = new StringBuilder(dir);
                filename.append("/");
                filename.append(getMessageId());
                filename.append(".msg");
                try (FileOutputStream fos = new FileOutputStream(filename.toString())) {
                    this.write(fos);
                    fos.flush();
                }
            }
            ExpiredMessageHandler h = c.getExpiryHandler(soapAction);
            if (h != null) {
                h.handleExpiry(this);
            }
        } catch (Exception e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.messaging.Sendable.expire", e);
        }
    }

    /**
     * Get the time this message was first sent.
     * @return 
     */
    public Calendar getStarted() {
        return (Calendar)started.clone();
    }

    /**
     * Gets the last time this message was retried. Note that this method returns a
     * clone of the actual "lastTry" Calendar held for the message. That is because
     * java.util.Calendar is NOT IMMUTABLE so if a clone is not returned, the attempt
     * to calculate the nearest next retry time advances the last retry time and so
     * the message never gets retried.
     * 
     * @return 
     */
    public Calendar lastTry() {
        return (Calendar) lastTry.clone();
    }

    /**
     * Called from the connection manager to see if it is OK to try to send
     * this. "Yes" if non-TMS-reliable (because we won't have seen it before,
     * otherwise), "yes" if we still have retries to do. A timer will sweep up
     * persistDuration expiries in a different thread so don't bother checking
     * that here.
     * @return 
     */
    public boolean recordTry() {
        if (retryCount < 1) {
            return true;
        }
        if (++tries > retryCount) {
            return false;
        }
        lastTry = Calendar.getInstance();
        return true;
    }

    public int getType() {
        return type;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int r) {
        retryCount = r;
    }

    public int getRetryInterval() {
        return minRetryInterval;
    }

    public void setRetryInterval(int r) {
        minRetryInterval = r;
    }

    public int getPersistDuration() {
        return persistDuration;
    }

    public void setPersistDuration(int r) {
        persistDuration = r;
    }

    public String getSynchronousResponse() {
        return synchronousResponse;
    }

    public void setSynchronousResponse(String r) {
        synchronousResponse = r;
    }

    public abstract void write(OutputStream s) throws Exception;

    public abstract void setResponse(Sendable r);

    public abstract Sendable getResponse();

    public abstract String getMessageId();

    public abstract void setMessageId(String s);

    public abstract String getResolvedUrl();

    public abstract String getHl7Payload();

    public abstract void persist() throws java.io.IOException;
    /**
     * A method to be used by any subclasses for substituting passed values into
     * a StringBuilder object using substitution tags
     *
     * @param sb StringBuilder representation of message to have substitutions
     * @param tag Substitution tags to be searched for and replaced
     * @param content Content which to substitute into the tags
     * @return Boolean response indicating if any substitutions have been made
     */
    protected boolean substitute(StringBuilder sb, String tag, String content) {
        boolean doneAnything = false;
        int tagPoint = -1;
        int tagLength = tag.length();
        if (content == null) {
            content = "";
        }
        while ((tagPoint = sb.indexOf(tag)) != -1) {
            sb.replace(tagPoint, tagPoint + tagLength, content);
            doneAnything = true;
        }
        return doneAnything;
    }
    public String getOnTheWireRequest() {
        return onTheWireRequest;
    }

    public void setOnTheWireRequest(String onTheWireRequest) {
        this.onTheWireRequest = onTheWireRequest;
            if(ConditionalCompilationControls.TESTHARNESS){
                if(ConditionalCompilationControls.otwMessageLogging){
                    SpineToolsLogger.getInstance().log("org.warlock.spine.messaging.sendable.message", "\r\nON THE WIRE OUTBOUND: \r\n\r\n"+ onTheWireRequest);
                }
            }

    }
}
