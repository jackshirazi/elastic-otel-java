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
package co.elastic.otel;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(AgentExtension.class)
public class ConfigLoggingExtension implements AgentExtension {
  private static final Logger logger = Logger.getLogger(ConfigLoggingExtension.class.getName());

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder, ConfigProperties config) {
    logConfig();
    return agentBuilder;
  }

  @Override
  public String extensionName() {
    return "elastic-config-logging";
  }

  public static void logConfig() {
    try {
      logger.info("GlobalOpenTelemetry: " + getLogConfigString());
    } catch (NoSuchFieldException
        | IllegalAccessException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException e) {
      logger.warning("Error getting 'delegate' from GlobalOpenTelemetry.get(): " + e.getMessage());
    }
  }

  static String getLogConfigString()
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException,
          NoSuchFieldException {
    // can't access GlobalOpenTelemetry directly because of shading conflicts
    //    OpenTelemetry obfuscatedConfig = GlobalOpenTelemetry.get();
    //    Field config = obfuscatedConfig.getClass().getDeclaredField("delegate");
    //    config.setAccessible(true);
    //    return config.get(obfuscatedConfig).toString();
    Class<?> globalOpenTelemetryClass = Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.api.GlobalOpenTelemetry");
    Method getMethod = globalOpenTelemetryClass.getDeclaredMethod("get");
    Object obfuscatedConfig = getMethod.invoke(null);
    Field config = obfuscatedConfig.getClass().getDeclaredField("delegate");
    config.setAccessible(true);
    return config.get(obfuscatedConfig).toString();
  }
}
