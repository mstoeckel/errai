/*
 * Copyright 2012 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.config.rebind;

import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.typeinfo.TypeOracleException;
import org.jboss.errai.common.metadata.ScannerSingleton;
import org.jboss.errai.common.rebind.CacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Mike Brock
 */
public final class AsyncGenerators {
  private static final Logger log = LoggerFactory.getLogger(AsyncGenerators.class);

  private AsyncGenerators() {
  }

  private static final Object lock = new Object();
  private static volatile boolean started = false;
  private static volatile GeneratorContext currentContext;

  private static final Map<Class, AsyncCodeGenerator> codeGenerators = new ConcurrentHashMap<Class, AsyncCodeGenerator>();
  private static final Map<Class, Future<String>> activeFutures = new ConcurrentHashMap<Class, Future<String>>();

  static Future<String> getFutureFor(final AsyncGenerationJob job) {
    synchronized (lock) {
      startAll(job);

      if (!codeGenerators.containsKey(job.getInterfaceType())) {
        throw new RuntimeException("no generator found for interface: " + job.getInterfaceType().getName());
      }

      if (activeFutures.containsKey(job.getInterfaceType())) {
        return activeFutures.get(job.getInterfaceType());
      }
      else {
        throw new RuntimeException("could not find future for interface: " + job.getInterfaceType().getName());
      }
    }
  }

  public static Future<String> getFutureFor(final Class clazz) {
    synchronized (lock) {

      if (!codeGenerators.containsKey(clazz)) {
        throw new RuntimeException("no generator found for interface: " + clazz.getName());
      }

      if (activeFutures.containsKey(clazz)) {
        return activeFutures.get(clazz);
      }
      else {
        throw new RuntimeException("could not find future for interface: " + clazz.getName());
      }
    }
  }

  private static class FutureWrapper implements Future<String> {
    private final Class interfaceType;
    private final Future<String> delegate;

    private FutureWrapper(Class interfaceType, Future<String> delegate) {
      this.interfaceType = interfaceType;
      this.delegate = delegate;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
      return delegate.isDone();
    }

    @Override
    public String get() throws InterruptedException, ExecutionException {
      final String val = delegate.get();
      activeFutures.remove(interfaceType);
      return val;
    }

    @Override
    public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      final String val = delegate.get(timeout, unit);
      activeFutures.remove(interfaceType);
      return val;
    }
  }

  private static void startAll(final AsyncGenerationJob job) {
    if (started && job.getGeneratorContext() != currentContext) {
      codeGenerators.clear();
      activeFutures.clear();
      started = false;
    }

    if (!started) {
      EnvUtil.recordEnvironmentState();
      CacheUtil.clearAll();

      started = true;
      currentContext = job.getGeneratorContext();

      job.notifyStarting();
      job.notifyStarted();

      for (final Class<?> cls : ScannerSingleton.getOrCreateInstance().getTypesAnnotatedWith(GenerateAsync.class)) {
        try {
          final AsyncCodeGenerator asyncCodeGenerator
              = cls.asSubclass(AsyncCodeGenerator.class).newInstance();

          final GenerateAsync generateAsync = cls.getAnnotation(GenerateAsync.class);

          try {
            job.getGeneratorContext().getTypeOracle().getType(generateAsync.value().getName());
            codeGenerators.put(generateAsync.value(), asyncCodeGenerator);

            log.info("discovered async generator " + cls.getName() + "; for type: " + generateAsync.value().getName());
          }
          catch (TypeOracleException e) {
            codeGenerators.remove(generateAsync.value());
            //  e.printStackTrace();
            // ignore because not inherited in an active module.
          }
        }
        catch (Throwable e) {
        }
      }

      for (final Map.Entry<Class, AsyncCodeGenerator> entry : codeGenerators.entrySet()) {
        final Future<String> value = entry.getValue().generateAsync(job.getTreeLogger(), job.getGeneratorContext());
        activeFutures.put(entry.getKey(), new FutureWrapper(entry.getKey(), value));

        log.info("started async generation for >> " + entry.getKey().getName());
      }
    }
    else {
      job.notifyStarted();
    }
  }
}
