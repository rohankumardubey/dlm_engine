/**
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

package org.apache.falcon.messaging;

import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.falcon.messaging.EntityInstanceMessage.ARG;

/**
 * Falcon JMS message creator- creates JMS TextMessage
 */
public class EntityInstanceMessageCreator {

	private MapMessage mapMessage;

	private final EntityInstanceMessage instanceMessage;

	public EntityInstanceMessageCreator(EntityInstanceMessage instanceMessage) {
		this.instanceMessage = instanceMessage;
	}

	public Message createMessage(Session session) throws JMSException {
		mapMessage = session.createMapMessage();
		for (Entry<ARG, String> entry : instanceMessage.getKeyValueMap()
				.entrySet()) {
			mapMessage.setString(entry.getKey().getArgName(), instanceMessage
					.getKeyValueMap().get(entry.getKey()));
		}
		return mapMessage;

	}

	@Override
	public String toString() {
		return this.mapMessage.toString();
	}

}
