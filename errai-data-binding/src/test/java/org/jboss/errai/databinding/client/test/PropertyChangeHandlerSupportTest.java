/*
 * Copyright 2012 JBoss, a division of Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.errai.databinding.client.test;

import static org.junit.Assert.assertEquals;

import org.jboss.errai.databinding.client.PropertyChangeHandlerSupport;
import org.jboss.errai.databinding.client.api.PropertyChangeEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link PropertyChangeHandlerSupport}.
 *
 * @author David Cracauer <dcracauer@gmail.com>
 */
public class PropertyChangeHandlerSupportTest {

  private PropertyChangeHandlerSupport support;

  @Before
  public void setUp() {
    support = new PropertyChangeHandlerSupport();
  }

  @Test
  public void testAdd() {
    MockHandler mh1 = new MockHandler();
    support.addPropertyChangeHandler(mh1);

    Object eventSource1 = new Object();
    PropertyChangeEvent event1 = new PropertyChangeEvent(eventSource1, "foo", 1, 2);

    support.notifyHandlers(event1);
    assertEquals(1, mh1.events.size());
    assertEquals(event1, mh1.events.get(0));
    assertEquals(eventSource1, mh1.events.get(0).getSource());

    MockHandler mh2 = new MockHandler();

    support.addPropertyChangeHandler(mh2);
    
    Object eventSource2 = new Object();
    PropertyChangeEvent event2 = new PropertyChangeEvent(eventSource2, "foo", 2, 3);

    support.notifyHandlers(event2);
    assertEquals(2, mh1.events.size());
    assertEquals(event2, mh1.events.get(1));
    assertEquals(eventSource2, mh1.events.get(1).getSource());
    assertEquals(1, mh2.events.size());
    assertEquals(event2, mh2.events.get(0));
    assertEquals(eventSource2, mh2.events.get(0).getSource());
  }

  @Test
  public void testAddForSpecificEvent() {
    MockHandler mh1 = new MockHandler();
    support.addPropertyChangeHandler("bar", mh1);

    MockHandler mh2 = new MockHandler();
    support.addPropertyChangeHandler("foo", mh2);

    Object eventSource1 = new Object();
    PropertyChangeEvent event1 = new PropertyChangeEvent(eventSource1, "foo", 1, 2);
    support.notifyHandlers(event1);
    assertEquals(0, mh1.events.size());
    assertEquals(1, mh2.events.size());
    assertEquals(event1, mh2.events.get(0));
    assertEquals(eventSource1, mh2.events.get(0).getSource());

    Object eventSource2 = new Object();
    PropertyChangeEvent event2 = new PropertyChangeEvent(eventSource2, "bar", 2, 3);
    support.notifyHandlers(event2);
    assertEquals(1, mh1.events.size());
    assertEquals(event2, mh1.events.get(0));
    assertEquals(eventSource2, mh1.events.get(0).getSource());
    assertEquals(1, mh2.events.size());
    assertEquals(event1, mh2.events.get(0));
    assertEquals(eventSource1, mh2.events.get(0).getSource());
  }

  @Test
  public void testRemove() {
    MockHandler mh1 = new MockHandler();
    support.addPropertyChangeHandler(mh1);

    Object eventSource1 = new Object();
    PropertyChangeEvent event1 = new PropertyChangeEvent(eventSource1, "foo", 1, 2);
    support.notifyHandlers(event1);
    assertEquals(1, mh1.events.size());
    assertEquals(event1, mh1.events.get(0));
    assertEquals(eventSource1, mh1.events.get(0).getSource());

    support.removePropertyChangeHandler(mh1);

    mh1.events.clear();

    Object eventSource2 = new Object();
    PropertyChangeEvent event2 = new PropertyChangeEvent(eventSource2, "foo", 2, 3);

    support.notifyHandlers(event2);
    assertEquals(0, mh1.events.size());
  }

  @Test
  public void testRemoveForSpecificEvent() {
    MockHandler mh1 = new MockHandler();
    support.addPropertyChangeHandler("bar", mh1);
    support.addPropertyChangeHandler("bar", mh1);

    Object eventSource = new Object();
    PropertyChangeEvent event1 = new PropertyChangeEvent(eventSource, "bar", 1, 2);
    support.notifyHandlers(event1);
    assertEquals(2, mh1.events.size());

    support.removePropertyChangeHandler("bar", mh1);
    PropertyChangeEvent event2 = new PropertyChangeEvent(eventSource, "bar", 2, 3);
    support.notifyHandlers(event2);
    assertEquals(3, mh1.events.size());

    support.removePropertyChangeHandler("bar", mh1);
    PropertyChangeEvent event3 = new PropertyChangeEvent(eventSource, "bar", 3, 4);
    support.notifyHandlers(event3);
    assertEquals(3, mh1.events.size());
  }

  @Test
  public void testNoChange() {
    MockHandler mh1 = new MockHandler();
    support.addPropertyChangeHandler(mh1);

    Object eventSource = new Object();
    support.notifyHandlers(new PropertyChangeEvent(eventSource, "foo", null, null));
    support.notifyHandlers(new PropertyChangeEvent(eventSource, "foo", 1, 1));
    support.notifyHandlers(new PropertyChangeEvent(eventSource, "foo", "test", "test"));
    assertEquals(0, mh1.events.size());
  }
}