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
package org.brixcms.web.reference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReferenceHasParametersTest {

    @Test
    public void onlyNamedParametersCountAsHavingParameters() {
        Reference ref = new Reference();
        ref.setType(Reference.Type.URL);
        ref.getParameters().set("lang", "de");

        // Regression: previously required BOTH indexed AND named parameters (&&), so a reference
        // carrying only named parameters (e.g. an empty target with "?lang=de") reported
        // hasParameters()==false and was silently dropped on save().
        assertTrue(ref.hasParameters());
    }

    @Test
    public void onlyIndexedParametersCountAsHavingParameters() {
        Reference ref = new Reference();
        ref.setType(Reference.Type.URL);
        ref.getParameters().set(0, "en");

        assertTrue(ref.hasParameters());
    }

    @Test
    public void bothParameterKindsCountAsHavingParameters() {
        Reference ref = new Reference();
        ref.setType(Reference.Type.URL);
        ref.getParameters().set(0, "en");
        ref.getParameters().set("lang", "de");

        assertTrue(ref.hasParameters());
    }

    @Test
    public void noParametersIsNotHavingParameters() {
        Reference ref = new Reference();
        ref.setType(Reference.Type.URL);

        assertFalse(ref.hasParameters());
    }
}
