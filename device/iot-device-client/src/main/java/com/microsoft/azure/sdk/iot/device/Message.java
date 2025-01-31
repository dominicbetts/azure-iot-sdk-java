// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.microsoft.azure.sdk.iot.device;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class Message
{
    // ----- Constants -----

    public static final Charset DEFAULT_IOTHUB_MESSAGE_CHARSET = StandardCharsets.UTF_8;

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss.SSSSSSS";

    private static final String SECURITY_CLIENT_JSON_ENCODING = "application/json";

    private static final String UTC_TIMEZONE = "UTC";

    // ----- Data Fields -----

    /**
     * [Required for two way requests] Used to correlate two-way communication.
     * Format: A case-sensitive string (up to 128 char long) of ASCII 7-bit alphanumeric chars
     * plus {'-', ':', '/', '\', '.', '+', '%', '_', '#', '*', '?', '!', '(', ')', ',', '=', '@', ';', '$', '''}.
     * Non-alphanumeric characters are from URN RFC.
     */
    private String messageId;

    /**
     * Destination of the message
     */
    private String to;

    /**
     * Expiry time in milliseconds. Optional
     */
    private long expiryTime;

    /**
     * Used in message responses and feedback
     */
    private String correlationId;

    /**
     * [Required in feedback messages] Used to specify the entity creating the message.
     */
    private String userId;

    /**
     * [Optional] Used when batching on HTTP Default: false.
     */
    private Boolean httpBatchSerializeAsString;

    /**
     * [Stamped on servicebound messages by IoT Hub] The authenticated id used to send this message.
     */
    private String connectionDeviceId;

    /**
     * [Optional] Used to specify the type of message exchanged between Iot Hub and Device
     */
    private MessageType messageType;

    /**
     * [Optional] Used to specify the sender device client for multiplexing scenarios
     */
    private IotHubConnectionString iotHubConnectionString;

    private String connectionModuleId;
    private String inputName;
    private String outputName;

    private String deliveryAcknowledgement;

    /**
     * User-defined properties.
     */
    private ArrayList<MessageProperty> properties;

    /**
     * The message body
     */
    private byte[] body;

    /**
     * Message routing options
     */
    private String contentType;
    private String contentEncoding;

    private Date creationTimeUTC;

    /**
     * Stream that will provide the bytes for the body of the
     */
    private ByteArrayInputStream bodyStream;

    /**
     * Security Client flag
     */
    boolean isSecurityClient;

    // ----- Constructors -----

    /**
     * Constructor.
     */
    public Message()
    {
        initialize();
    }

    /**
     * Constructor.
     * @param stream A stream to provide the body of the new Message instance.
     */
    public Message(ByteArrayInputStream stream)
    {
        initialize();
    }

    /**
     * Constructor.
     * @param body The body of the new Message instance.
     */
    public Message(byte[] body)
    {
        // Codes_SRS_MESSAGE_11_025: [If the message body is null, the constructor shall throw an IllegalArgumentException.]
        if (body == null)
        {
            throw new IllegalArgumentException("Message body cannot be 'null'.");
        }

        initialize();

        // Codes_SRS_MESSAGE_11_024: [The constructor shall save the message body.]
        this.body = body;
    }

    /**
     * Constructor.
     * @param body The body of the new Message instance. It is internally serialized to a byte array using UTF-8 encoding.
     */
    public Message(String body)
    {
        if (body == null)
        {
            throw new IllegalArgumentException("Message body cannot be 'null'.");
        }

        initialize();

        this.body = body.getBytes(DEFAULT_IOTHUB_MESSAGE_CHARSET);
        this.setContentTypeFinal(DEFAULT_IOTHUB_MESSAGE_CHARSET.name());
    }

    
    // ----- Public Methods -----

    /**
     * The stream content of the body.
     * @return always returns null.
     */
    @SuppressWarnings("SameReturnValue")
    public ByteArrayOutputStream getBodyStream()
    {
        return null;
    }

    /**
     * The byte content of the body.
     * @return A copy of this Message body, as a byte array.
     */
    public byte[] getBytes()
    {
        // Codes_SRS_MESSAGE_11_002: [The function shall return the message body.]
        byte[] bodyClone = null;

        if (this.body != null) {
            bodyClone = Arrays.copyOf(this.body, this.body.length);
        }

        return bodyClone;
    }

    /**
     * Gets the values of user-defined properties of this Message.
     * @param name Name of the user-defined property to search for.
     * @return The value of the property if it is set, or null otherwise.
     */
    public String getProperty(String name)
    {
        MessageProperty messageProperty = null;

        for (MessageProperty currentMessageProperty: this.properties)
        {
            if (currentMessageProperty.hasSameName(name))
            {
                messageProperty = currentMessageProperty;
                break;
            }
        }

        // Codes_SRS_MESSAGE_11_034: [If no value associated with the property name is found, the function shall return null.]
        if (messageProperty == null) {
            return null;
        }

        // Codes_SRS_MESSAGE_11_032: [The function shall return the value associated with the message property name, where the name can be either the HTTPS or AMQPS property name.]
        return messageProperty.getValue();
    }

    /**
     * Adds or sets user-defined properties of this Message.
     * @param name Name of the property to be set.
     * @param value Value of the property to be set.
     * @exception IllegalArgumentException If any of the arguments provided is null.
     */
    public void setProperty(String name, String value)
    {
        // Codes_SRS_MESSAGE_11_028: [If name is null, the function shall throw an IllegalArgumentException.]
        if (name == null)
        {
            throw new IllegalArgumentException("Property name cannot be 'null'.");
        }

        // Codes_SRS_MESSAGE_11_029: [If value is null, the function shall throw an IllegalArgumentException.]
        if (value == null)
        {
            throw new IllegalArgumentException("Property value cannot be 'null'.");
        }

        // Codes_SRS_MESSAGE_11_026: [The function shall set the message property to the given value.]
        MessageProperty messageProperty = null;

        for (MessageProperty currentMessageProperty: this.properties)
        {
            if (currentMessageProperty.hasSameName(name))
            {
                messageProperty = currentMessageProperty;
                break;
            }
        }

        if (messageProperty != null)
        {
            this.properties.remove(messageProperty);
        }

        this.properties.add(new MessageProperty(name, value));
    }

    /**
     * Returns a copy of the message properties.
     *
     * @return a copy of the message properties.
     */
    public MessageProperty[] getProperties()
    {
        // Codes_SRS_MESSAGE_11_033: [The function shall return a copy of the message properties.]
        return properties.toArray(new MessageProperty[this.properties.size()]);
    }

    // ----- Private Methods -----

    /**
     * Internal initializer method for a new Message instance.
     */
    private void initialize()
    {
        this.messageId = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
        this.properties = new ArrayList<>();
        this.isSecurityClient = false;
    }

    /**
     * Verifies whether the message is expired or not
     * @return true if the message is expired, false otherwise
     */
    public boolean isExpired()
    {
        boolean messageExpired;

        // Codes_SRS_MESSAGE_15_035: [The function shall return false if the expiryTime is set to 0.]
        if (this.expiryTime == 0)
        {
            messageExpired = false;
        }
        else
        {
            // Codes_SRS_MESSAGE_15_036: [The function shall return true if the current time is greater than the expiry time and false otherwise.]
            long currentTime = System.currentTimeMillis();
            if (currentTime > expiryTime)
            {
                log.warn("The message with correlation id {} expired", this.getCorrelationId());
                messageExpired = true;
            }
            else
            {
                messageExpired = false;
            }
        }

        return messageExpired;
    }

    /**
     * Getter for the messageId property
     * @return The property value
     */
    public String getMessageId()
    {
        // Codes_SRS_MESSAGE_34_043: [The function shall return the message's message Id.]
        return messageId;
    }

    /**
     * Setter for the messageId property
     * @param messageId The string containing the property value
     */
    public void setMessageId(String messageId)
    {
        // Codes_SRS_MESSAGE_34_044: [The function shall set the message's message ID to the provided value.]
        this.messageId = messageId;
    }

    public void setUserId(String userId)
    {
        // Codes_SRS_MESSAGE_34_050: [The function shall set the message's user ID to the provided value.]
        this.userId = userId;
    }

    /**
     * Getter for the correlationId property
     * @return The property value
     */
    public String getCorrelationId()
    {
        // Codes_SRS_MESSAGE_34_045: [The function shall return the message's correlation ID.]
        if (correlationId == null)
        {
            return "";
        }

        return correlationId;
    }

    /**
     * Setter for the correlationId property
     * @param correlationId The string containing the property value
     */
    public void setCorrelationId(String correlationId)
    {
        // Codes_SRS_MESSAGE_34_046: [The function shall set the message's correlation ID to the provided value.]
        this.correlationId = correlationId;
    }

    /**
     * Setter for the expiryTime property. This setter uses relative time, not absolute time.
     * @param timeOut The time out for the message, in milliseconds, from the current time.
     */
    public void setExpiryTime(long timeOut)
    {
        // Codes_SRS_MESSAGE_34_047: [The function shall set the message's expiry time.]
        long currentTime = System.currentTimeMillis();
        this.expiryTime = currentTime + timeOut;
        log.trace("The message with messageid {} has expiry time in {} milliseconds and the message will expire on {}", this.getMessageId(), timeOut, new Date(this.expiryTime));
    }

    /**
     * Setter for the expiryTime property using absolute time
     * @param absoluteTimeout The time out for the message, in milliseconds.
     */
    public void setAbsoluteExpiryTime(long absoluteTimeout)
    {
        // Codes_SRS_MESSAGE_34_038: [If the provided absolute expiry time is negative, an IllegalArgumentException shall be thrown.]
        if (absoluteTimeout < 0)
        {
            throw new IllegalArgumentException("ExpiryTime may not be negative");
        }

        // Codes_SRS_MESSAGE_34_037: [The function shall set the message's expiry time to be the number of milliseconds since the epoch provided in absoluteTimeout.]
        this.expiryTime = absoluteTimeout;
    }

    /**
     * Getter for the Message type
     * @return the Message type value
     */
    public MessageType getMessageType()
    {
        // Codes_SRS_MESSAGE_34_049: [The function shall return the message's message type.]
        return this.messageType;
    }

    public void setConnectionDeviceId(String connectionDeviceId)
    {
        // Codes_SRS_MESSAGE_34_051: [The function shall set the message's connection device id to the provided value.]
        this.connectionDeviceId = connectionDeviceId;
    }

    public void setConnectionModuleId(String connectionModuleId)
    {
        // Codes_SRS_MESSAGE_34_052: [The function shall set the message's connection module id to the provided value.]
        this.connectionModuleId = connectionModuleId;
    }

    /**
     * Set the output channel name to send to. Used in routing for module communications
     * @param outputName the output channel name to send to
     */
    public void setOutputName(String outputName)
    {
        // Codes_SRS_MESSAGE_34_053: [The function shall set the message's output name to the provided value.]
        this.outputName = outputName;
    }

    /**
     * Set the input name of the message, used in routing for module communications
     * @param inputName the input channel the message was received from
     */
    public void setInputName(String inputName)
    {
        // Codes_SRS_MESSAGE_34_058: [The function shall set the message's input name to the provided value.]
        this.inputName = inputName;
    }

    /**
     * Setter for the Message type
     * @param type The enum containing the Message type value
     */
    public void setMessageType(MessageType type)
    {
        // Codes_SRS_MESSAGE_34_048: [The function shall set the message's message type.]
        this.messageType = type;
    }

    /**
     * Getter for the To system property
     * @return the To value
     */
    public String getTo()
    {
        // Codes_SRS_MESSAGE_34_041: [The function shall return the message's To value.]
        return this.to;
    }

    public String getConnectionDeviceId()
    {
        // Codes_SRS_MESSAGE_34_054: [The function shall return the message's connection device id value.]
        return connectionDeviceId;
    }

    public String getConnectionModuleId()
    {
        // Codes_SRS_MESSAGE_34_055: [The function shall return the message's connection module id value.]
        return connectionModuleId;
    }

    public String getInputName()
    {
        // Codes_SRS_MESSAGE_34_056: [The function shall return the message's input name value.]
        return inputName;
    }

    public String getOutputName()
    {
        // Codes_SRS_MESSAGE_34_057: [The function shall return the message's output name value.]
        return outputName;
    }

    /**
     * Getter for the delivery acknowledgement system property
     * @return the delivery acknowledgement value
     */
    public String getDeliveryAcknowledgement()
    {
        // Codes_SRS_MESSAGE_34_039: [The function shall return the message's DeliveryAcknowledgement.]
        return this.deliveryAcknowledgement;
    }

    /**
     * Getter for the User ID system property
     * @return the User ID value
     */
    public String getUserId ()
    {
        // Codes_SRS_MESSAGE_34_037: [The function shall return the message's user ID.]
        return this.userId;
    }

    /**
     * Getter for the iotHubConnectionString property
     * @return the iotHubConnectionString value
     */
    public IotHubConnectionString getIotHubConnectionString()
    {
        // Codes_SRS_MESSAGE_12_001: [The function shall return the message's iotHubConnectionString object.]
        return iotHubConnectionString;
    }

    /**
     * Setter for the iotHubConnectionString type
     * @param iotHubConnectionString The iotHubConnectionString value to set
     */
    public void setIotHubConnectionString(IotHubConnectionString iotHubConnectionString)
    {
        // Codes_SRS_MESSAGE_12_002: [The function shall set the message's iotHubConnectionString object to the provided value.]
        this.iotHubConnectionString = iotHubConnectionString;
    }

    /**
     * Return the message's content type. This value is null by default
     * @return the message's content type
     */
    public String getContentType()
    {
        // Codes_SRS_MESSAGE_34_059: [The function shall return the message's content type.]
        return this.contentType;
    }

    /**
     * Set the content type of this message. Used in message routing.
     *
     * @deprecated as of device-client version 1.14.1, please use {@link #setContentTypeFinal(String)}
     *
     *  @param contentType the content type of the message. May be null if you don't want to specify a content type.
     */
    @Deprecated
    public void setContentType(String contentType)
    {
        // Codes_SRS_MESSAGE_34_060: [The function shall save the provided content type.]
        this.contentType = contentType;
    }

    /**
     * Set the content type of this message. Used in message routing.
     * @param contentType the content type of the message. May be null if you don't want to specify a content type.
     */
    public final void setContentTypeFinal(String contentType)
    {
        // Codes_SRS_MESSAGE_34_060: [The function shall save the provided content type.]
        this.contentType = contentType;
    }

    /**
     * Returns this message's content encoding. This value is null by default
     * @return the message's content encoding.
     */
    public String getContentEncoding()
    {
        // Codes_SRS_MESSAGE_34_061: [The function shall return the message's content encoding.]
        return this.contentEncoding;
    }

    /**
     * Set the content encoding of this message. Used in message routing.
     * @param contentEncoding the content encoding of the message. May be null if you don't want to specify a content encoding.
     */
    public void setContentEncoding(String contentEncoding)
    {
        // Codes_SRS_MESSAGE_34_062: [The function shall save the provided content encoding.]
        this.contentEncoding = contentEncoding;
    }

    public Date getCreationTimeUTC()
    {
        // Codes_SRS_MESSAGE_34_063: [The function shall return the saved creationTimeUTC.]
        return this.creationTimeUTC;
    }

    /**
     * Returns the iot hub accepted format for the creation time utc
     *
     * ex:
     * oct 1st, 2018 yields
     * 2008-10-01T17:04:32.0000000
     *
     * @return the iot hub accepted format for the creation time utc
     */
    public String getCreationTimeUTCString()
    {
        if (this.creationTimeUTC == null)
        {
            return null;
        }

        // Codes_SRS_MESSAGE_34_064: [The function shall return the saved creationTimeUTC as a string in the format "yyyy-MM-dd_HH:mm:ss.SSSSSSSZ".]
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone(UTC_TIMEZONE));
        return sdf.format(this.creationTimeUTC).replace("_", "T") + "Z";
    }

    public final void setCreationTimeUTC(Date creationTimeUTC)
    {
        // Codes_SRS_MESSAGE_34_065: [The function shall save the provided creationTimeUTC.]
        this.creationTimeUTC = creationTimeUTC;
    }

    public void setAsSecurityMessage()
    {
        // Set the message as json encoding
        this.contentEncoding = SECURITY_CLIENT_JSON_ENCODING;
        this.isSecurityClient = true;
    }

    public boolean isSecurityMessage()
    {
        return this.isSecurityClient;
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append(" Message details: ");
        if (this.correlationId != null && !this.correlationId.isEmpty())
        {
            s.append("Correlation Id [").append(this.correlationId).append("] ");
        }

        if (this.messageId != null && !this.messageId.isEmpty())
        {
            s.append("Message Id [").append(this.messageId).append("] ");
        }

        return s.toString();
    }
}