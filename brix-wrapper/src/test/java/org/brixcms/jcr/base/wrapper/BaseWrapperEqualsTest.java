/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.brixcms.jcr.base.wrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BaseWrapperEqualsTest {

    @Test
    public void wrappersWithSameDelegateAreEqual() {
        Object delegate = new Object();
        BaseWrapper<Object> a = new BaseWrapper<Object>(delegate, null);
        BaseWrapper<Object> b = new BaseWrapper<Object>(delegate, null);

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void wrappersWithEqualButNotIdenticalDelegateAreEqual() {
        // distinct String instances that are equal via String#equals but not ==
        BaseWrapper<Object> a = new BaseWrapper<Object>(new String("node-1"), null);
        BaseWrapper<Object> b = new BaseWrapper<Object>(new String("node-1"), null);

        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void wrappersWithDifferentDelegatesAreNotEqual() {
        BaseWrapper<Object> a = new BaseWrapper<Object>("node-1", null);
        BaseWrapper<Object> b = new BaseWrapper<Object>("node-2", null);

        assertFalse(a.equals(b));
    }

    @Test
    public void nonWrapperArgumentReturnsFalseWithoutClassCastException() {
        BaseWrapper<Object> a = new BaseWrapper<Object>("node-1", null);

        // Regression: previously the instanceof guard was inverted, so any non-BaseWrapper argument
        // fell through to a cast and threw ClassCastException, and two wrappers were never equal.
        assertFalse(a.equals("node-1"));
        assertFalse(a.equals(null));
        assertFalse(a.equals(42));
    }
}
