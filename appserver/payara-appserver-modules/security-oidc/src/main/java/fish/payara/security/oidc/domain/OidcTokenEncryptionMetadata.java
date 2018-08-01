/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) [2018] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */
package fish.payara.security.oidc.domain;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import java.text.ParseException;
import org.glassfish.common.util.StringHelper;

/**
 * OIDC Id token encryption configuration (algo, method and key)
 *
 * @author Gaurav Gupta
 */
public class OidcTokenEncryptionMetadata {

    private JWEAlgorithm encryptionAlgorithm;
    private EncryptionMethod encryptionMethod;
    private JWKSource privateKeySource;

    public JWEAlgorithm getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public OidcTokenEncryptionMetadata setEncryptionAlgorithm(JWEAlgorithm encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
        return this;
    }

    public OidcTokenEncryptionMetadata setEncryptionAlgorithm(String encryptionAlgorithm) {
        if (!StringHelper.isEmpty(encryptionAlgorithm)) {
            this.encryptionAlgorithm = JWEAlgorithm.parse(encryptionAlgorithm);
        }
        return this;
    }

    public EncryptionMethod getEncryptionMethod() {
        return encryptionMethod;
    }

    public OidcTokenEncryptionMetadata setEncryptionMethod(EncryptionMethod encryptionMethod) {
        this.encryptionMethod = encryptionMethod;
        return this;
    }

    public OidcTokenEncryptionMetadata setEncryptionMethod(String encryptionMethod) {
        if (!StringHelper.isEmpty(encryptionMethod)) {
            this.encryptionMethod = EncryptionMethod.parse(encryptionMethod);
        }
        return this;
    }

    public JWKSource getPrivateKeySource() {
        return privateKeySource;
    }

    public OidcTokenEncryptionMetadata setPrivateKeySource(JWKSource privateKeySource) {
        this.privateKeySource = privateKeySource;
        return this;
    }

    public OidcTokenEncryptionMetadata setPrivateKeySource(String jwks) {
        if (!StringHelper.isEmpty(jwks)) {
            try {
                this.privateKeySource = new ImmutableJWKSet(JWKSet.parse(jwks));
            } catch (ParseException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return OidcTokenEncryptionMetadata.class.getSimpleName()
                + "{"
                + "encryptionAlgorithm=" + encryptionAlgorithm
                + ", encryptionMethod=" + encryptionMethod
                + '}';
    }

}
