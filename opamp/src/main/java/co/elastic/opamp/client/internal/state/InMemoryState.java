/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.opamp.client.internal.state;

import co.elastic.opamp.client.state.State;

public class InMemoryState<T> extends State<T> {
  private T state;

  public InMemoryState(T initialState) {
    this.state = initialState;
  }

  public synchronized void set(T value) {
    if (!areEqual(state, value)) {
      state = value;
      notifyObservers();
    }
  }

  @Override
  public synchronized T get() {
    return state;
  }

  protected boolean areEqual(T first, T second) {
    return first.equals(second);
  }
}
