/*******************************************************************************
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
 ******************************************************************************/
package org.apache.ivory.entity.v0.datastore.adapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.apache.ivory.entity.v0.datastore.Option;
import org.apache.ivory.entity.v0.datastore.Options;

public class OptionsMapAdapter extends XmlAdapter<Options, Map<String, String>> {

  @Override
  public Options marshal(Map<String, String> v) throws Exception {
    if (v == null) return null;
    Options options = new Options();
    List<Option> option = options.getOption();
    for (Map.Entry<String, String> entry : v.entrySet()) {
      option.add(new Option(entry.getKey(), entry.getValue()));
    }
    return options;
  }

  @Override
  public Map<String, String> unmarshal(Options options) throws Exception {
    if (options == null) return null;
    Map<String, String> map = new HashMap<String, String>();
    for (Option option : options.getOption()) {
      map.put(option.getName(), option.getValue());
    }
    return map;
  }

}
