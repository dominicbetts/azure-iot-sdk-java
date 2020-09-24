// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.microsoft.azure.sdk.iot.service.digitaltwin.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.sdk.iot.service.digitaltwin.generated.models.DigitalTwinGetHeaders;
import com.microsoft.azure.sdk.iot.service.digitaltwin.generated.models.DigitalTwinInvokeComponentCommandHeaders;
import com.microsoft.azure.sdk.iot.service.digitaltwin.generated.models.DigitalTwinInvokeRootLevelCommandHeaders;
import com.microsoft.azure.sdk.iot.service.digitaltwin.models.DigitalTwinCommandResponse;
import com.microsoft.azure.sdk.iot.service.digitaltwin.models.DigitalTwinInvokeCommandHeaders;
import com.microsoft.rest.ServiceResponseWithHeaders;
import rx.Observable;
import rx.functions.Func1;

public final class Tools {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final Func1<Object, Observable<String>> FUNC_MAP_TO_JSON_STRING = object -> {
        try {
            return Observable.just(objectMapper.writeValueAsString(object));
        }
        catch (JsonProcessingException e) {
            return Observable.error(e);
        }
    };

    public static final Func1<ServiceResponseWithHeaders<Object, DigitalTwinGetHeaders>, Observable<ServiceResponseWithHeaders<String, DigitalTwinGetHeaders>>> FUNC_MAP_WITH_RESPONSE_TO_JSON_STRING = object -> {
        try {
            ServiceResponseWithHeaders<String, DigitalTwinGetHeaders> result = new ServiceResponseWithHeaders<String, DigitalTwinGetHeaders>(objectMapper.writeValueAsString(object.body()), object.headers(), object.response());
            return Observable.just(result);
        }
        catch (JsonProcessingException e) {
            return Observable.error(e);
        }
    };

    public static final Func1<ServiceResponseWithHeaders<Object, DigitalTwinInvokeRootLevelCommandHeaders>, Observable<DigitalTwinCommandResponse>> FUNC_TO_DIGITAL_TWIN_COMMAND_RESPONSE = object -> {
        try {
            DigitalTwinCommandResponse digitalTwinCommandResponse = new DigitalTwinCommandResponse();
            digitalTwinCommandResponse.setPayload(objectMapper.writeValueAsString(object.body()));
            digitalTwinCommandResponse.setStatus(object.headers().xMsCommandStatuscode());
            return Observable.just(digitalTwinCommandResponse);
        }
        catch (JsonProcessingException e) {
            return Observable.error(e);
        }

    };

    public static final Func1<ServiceResponseWithHeaders<Object, DigitalTwinInvokeRootLevelCommandHeaders>, Observable<ServiceResponseWithHeaders<DigitalTwinCommandResponse, DigitalTwinInvokeCommandHeaders>>> FUNC_TO_DIGITAL_TWIN_COMMAND_RESPONSE_WITH_HEADERS = object -> {
        try {
            DigitalTwinCommandResponse digitalTwinCommandResponse = new DigitalTwinCommandResponse();
            digitalTwinCommandResponse.setPayload(objectMapper.writeValueAsString(object.body()));
            digitalTwinCommandResponse.setStatus(object.headers().xMsCommandStatuscode());
            DigitalTwinInvokeCommandHeaders digitalTwinInvokeCommandHeaders = new DigitalTwinInvokeCommandHeaders();
            digitalTwinInvokeCommandHeaders.setRequestId(object.headers().xMsRequestId());
            ServiceResponseWithHeaders<DigitalTwinCommandResponse, DigitalTwinInvokeCommandHeaders> result = new ServiceResponseWithHeaders<>(digitalTwinCommandResponse, digitalTwinInvokeCommandHeaders, object.response());
            return Observable.just(result);
        }
        catch (JsonProcessingException e) {
            return Observable.error(e);
        }

    };

    public static final Func1<ServiceResponseWithHeaders<Object, DigitalTwinInvokeComponentCommandHeaders>, Observable<DigitalTwinCommandResponse>> FUNC_TO_DIGITAL_TWIN_COMPONENT_COMMAND_RESPONSE = object -> {
        try {
            DigitalTwinCommandResponse digitalTwinCommandResponse = new DigitalTwinCommandResponse();
            digitalTwinCommandResponse.setPayload(objectMapper.writeValueAsString(object.body()));
            digitalTwinCommandResponse.setStatus(object.headers().xMsCommandStatuscode());
            return Observable.just(digitalTwinCommandResponse);
        }
        catch (JsonProcessingException e) {
            return Observable.error(e);
        }

    };

    public static final Func1<ServiceResponseWithHeaders<Object, DigitalTwinInvokeComponentCommandHeaders>, Observable<ServiceResponseWithHeaders<DigitalTwinCommandResponse, DigitalTwinInvokeCommandHeaders>>> FUNC_TO_DIGITAL_TWIN_COMPONENT_COMMAND_RESPONSE_WITH_HEADERS = object -> {
        try {
            DigitalTwinCommandResponse digitalTwinCommandResponse = new DigitalTwinCommandResponse();
            digitalTwinCommandResponse.setPayload(objectMapper.writeValueAsString(object.body()));
            digitalTwinCommandResponse.setStatus(object.headers().xMsCommandStatuscode());
            DigitalTwinInvokeCommandHeaders digitalTwinInvokeCommandHeaders = new DigitalTwinInvokeCommandHeaders();
            digitalTwinInvokeCommandHeaders.setRequestId(object.headers().xMsRequestId());
            ServiceResponseWithHeaders<DigitalTwinCommandResponse, DigitalTwinInvokeCommandHeaders> result = new ServiceResponseWithHeaders<>(digitalTwinCommandResponse, digitalTwinInvokeCommandHeaders, object.response());
            return Observable.just(result);
        }
        catch (JsonProcessingException e) {
            return Observable.error(e);
        }

    };

    /**
     * Empty private constructor to prevent accidental creation of instances
     */
    private Tools() {

    }

    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
