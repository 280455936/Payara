/*
 *    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *    Copyright (c) [2019] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *    The contents of this file are subject to the terms of either the GNU
 *    General Public License Version 2 only ("GPL") or the Common Development
 *    and Distribution License("CDDL") (collectively, the "License").  You
 *    may not use this file except in compliance with the License.  You can
 *    obtain a copy of the License at
 *    https://github.com/payara/Payara/blob/master/LICENSE.txt
 *    See the License for the specific
 *    language governing permissions and limitations under the License.
 *
 *    When distributing the software, include this License Header Notice in each
 *    file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *    GPL Classpath Exception:
 *    The Payara Foundation designates this particular file as subject to the "Classpath"
 *    exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *    file that accompanied this code.
 *
 *    Modifications:
 *    If applicable, add the following below the License Header, with the fields
 *    enclosed by brackets [] replaced by your own identifying information:
 *    "Portions Copyright [year] [name of copyright owner]"
 *
 *    Contributor(s):
 *    If you wish your version of this file to be governed by only the CDDL or
 *    only the GPL Version 2, indicate your decision by adding "[Contributor]
 *    elects to include this software in this distribution under the [CDDL or GPL
 *    Version 2] license."  If you don't indicate a single choice of license, a
 *    recipient has the option to distribute your version of this file under
 *    either the CDDL, the GPL Version 2 or to extend the choice of license to
 *    its licensees as provided above.  However, if you add GPL Version 2 code
 *    and therefore, elected the GPL Version 2 license, then the option applies
 *    only if the new code is made subject to such option by the copyright
 *    holder.
 */

package fish.payara.ejb.http.client.adapter;

import org.junit.Test;

import javax.naming.Context;
import javax.naming.NamingException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class ClientAdapterRegistryTest {
    @Test
    public void builtRegistryIsUnmodifiable() throws NamingException {
        ClientAdapterRegistry.Builder b = ClientAdapterRegistry.newBuilder().register(ClientAdapterRegistryTest::returnEmpty);
        ClientAdapterRegistry registry = b.build();
        b.register(returnConstant("VALUE")).build();

        assertEquals("Additional adapter should not be considered in already built registry",
                Optional.empty(),
                registry.makeClientAdapter("any", null));
    }

    @Test
    public void adaptersAreEvaluatedInOrder() throws NamingException {
        ClientAdapterRegistry registry = ClientAdapterRegistry.newBuilder()
                .register(ClientAdapterRegistryTest::returnEmpty, returnConstant("one"), returnConstant("two"))
                .build();
        assertEquals(Optional.of("one"), registry.makeClientAdapter("any", null));
    }

    @Test(expected = NamingException.class)
    public void namingExceptionStopsIterationAndPropagates() throws NamingException {
        ClientAdapterRegistry registry = ClientAdapterRegistry.newBuilder()
                .register(ClientAdapterRegistryTest::throwNamingException, returnConstant("one")).build();
        registry.makeClientAdapter("any", null);
    }

    @Test
    public void suppliersAreCalledForEachLookup() throws NamingException {
        AtomicInteger counter = new AtomicInteger();
        Supplier<ClientAdapter> adapterFactory = () -> {
            int number = counter.incrementAndGet();
            return returnConstant(number);
        };

        ClientAdapterRegistry registry = ClientAdapterRegistry.newBuilder().register(adapterFactory).build();
        assertEquals(Optional.of(1), registry.makeClientAdapter("any", null));
        assertEquals(Optional.of(2), registry.makeClientAdapter("any", null));
    }

    @Test
    public void registeredClassIsInstantiatedOnce() throws NamingException {
        assertEquals(0, InstantiatedClientAdapter.instantiationCount);
        ClientAdapterRegistry registry = ClientAdapterRegistry.newBuilder().register(InstantiatedClientAdapter.class).build();

        assertEquals(1, InstantiatedClientAdapter.instantiationCount);
        assertEquals(Optional.empty(), registry.makeClientAdapter("any", null));

        assertEquals(1, InstantiatedClientAdapter.instantiationCount);
    }

    static Optional<Object> returnEmpty(String name, Context remoteContext) {
        return Optional.empty();
    }

    static ClientAdapter returnConstant(Object value) {
        return (name, context) -> Optional.of(value);
    }

    static Optional<Object> throwNamingException(String name, Context remoteContext) throws NamingException {
        throw new NamingException("That didn't work out");
    }

    static class InstantiatedClientAdapter implements ClientAdapter {
        static int instantiationCount = 0;

        public InstantiatedClientAdapter() {
            instantiationCount++;
        }

        @Override
        public Optional<Object> makeClientAdapter(String jndiName, Context remoteContext) throws NamingException {
            return Optional.empty();
        }
    }
}
