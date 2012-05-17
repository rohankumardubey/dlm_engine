/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ivory;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class IvoryWebException extends WebApplicationException {

    private static final Logger LOG = Logger.getLogger(IvoryWebException.class);

    public static IvoryWebException newException(Throwable e,
                                                 Response.Status status) {
        LOG.error("Action failed: " + status, e);
        return new IvoryWebException(Response.status(status).
                entity(getMessage(e)+ "\n" + getAddnInfo(e)).
                type(MediaType.TEXT_PLAIN_TYPE).build());
    }

    public static IvoryWebException newException(String message,
                                                 Response.Status status) {
        return new IvoryWebException(Response.status(status).
                entity(message).type(MediaType.TEXT_PLAIN_TYPE).build());
    }

    private static String getMessage(Throwable e) {
        if(StringUtils.isEmpty(e.getMessage()))
            return e.getClass().getName();
        return e.getMessage();
    }

    private static String getAddnInfo(Throwable e) {
        String addnInfo = "";
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !getMessage(e).contains(cause.getMessage())) {
            addnInfo = cause.getMessage();
        }
        return addnInfo;
    }

    public IvoryWebException(Response response) {
        super(response);
    }

}
