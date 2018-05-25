/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *    Copyright (c) [2018] Payara Foundation and/or its affiliates. All rights reserved.
 * 
 *     The contents of this file are subject to the terms of either the GNU
 *     General Public License Version 2 only ("GPL") or the Common Development
 *     and Distribution License("CDDL") (collectively, the "License").  You
 *     may not use this file except in compliance with the License.  You can
 *     obtain a copy of the License at
 *     https://github.com/payara/Payara/blob/master/LICENSE.txt
 *     See the License for the specific
 *     language governing permissions and limitations under the License.
 * 
 *     When distributing the software, include this License Header Notice in each
 *     file and include the License file at glassfish/legal/LICENSE.txt.
 * 
 *     GPL Classpath Exception:
 *     The Payara Foundation designates this particular file as subject to the "Classpath"
 *     exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *     file that accompanied this code.
 * 
 *     Modifications:
 *     If applicable, add the following below the License Header, with the fields
 *     enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyright [year] [name of copyright owner]"
 * 
 *     Contributor(s):
 *     If you wish your version of this file to be governed by only the CDDL or
 *     only the GPL Version 2, indicate your decision by adding "[Contributor]
 *     elects to include this software in this distribution under the [CDDL or GPL
 *     Version 2] license."  If you don't indicate a single choice of license, a
 *     recipient has the option to distribute your version of this file under
 *     either the CDDL, the GPL Version 2 or to extend the choice of license to
 *     its licensees as provided above.  However, if you add GPL Version 2 code
 *     and therefore, elected the GPL Version 2 license, then the option applies
 *     only if the new code is made subject to such option by the copyright
 *     holder.
 */
package fish.payara.opentracing.span;

import fish.payara.notification.requesttracing.RequestTraceSpan;
import fish.payara.notification.requesttracing.RequestTraceSpanLog;
import fish.payara.nucleus.requesttracing.RequestTracingService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.internal.api.Globals;

/**
 *
 * @author Andrew Pielage <andrew.pielage@payara.fish>
 */
public class Span extends RequestTraceSpan implements io.opentracing.Span {
    
    private RequestTracingService requestTracing;
    
    @PostConstruct
    public void postConstruct() {
        ServiceLocator serviceLocator = Globals.getDefaultBaseServiceLocator();

        if (serviceLocator != null) {
            requestTracing = serviceLocator.getService(RequestTracingService.class);
        }
    }

    public Span(String operationName) {
        super(operationName);
    }

    @Override
    public io.opentracing.SpanContext context() {
        return (SpanContext) getSpanContext();
    }

    @Override
    public io.opentracing.Span setTag(String tagName, String tagValue) {
        addSpanTag(tagName, tagValue);
        return this;
    }

    @Override
    public io.opentracing.Span setTag(String tagName, boolean tagValue) {
        addSpanTag(tagName, Boolean.toString(tagValue));
        return this;
    }

    @Override
    public io.opentracing.Span setTag(String tagName, Number tagValue) {
        addSpanTag(tagName, String.valueOf(tagValue));
        return this;
    }

    @Override
    public io.opentracing.Span log(Map<String, ?> map) {
        RequestTraceSpanLog spanLog = new RequestTraceSpanLog();

        for (Map.Entry<String, ?> entry : map.entrySet()) {
            spanLog.addLogEntry(entry.getKey(), String.valueOf(entry.getValue()));
        }

        addSpanLog(spanLog);

        return this;
    }

    @Override
    public io.opentracing.Span log(long timestampMicroseconds, Map<String, ?> map) {
        RequestTraceSpanLog spanLog = new RequestTraceSpanLog(
                convertTimestampMicrosToTimestampMillis(timestampMicroseconds));

        for (Map.Entry<String, ?> entry : map.entrySet()) {
            spanLog.addLogEntry(entry.getKey(), String.valueOf(entry.getValue()));
        }

        addSpanLog(spanLog);

        return this;
    }

    @Override
    public io.opentracing.Span log(String logEvent) {
        RequestTraceSpanLog spanLog = new RequestTraceSpanLog(logEvent);
        addSpanLog(spanLog);

        return this;
    }

    @Override
    public io.opentracing.Span log(long timestampMicroseconds, String logEvent) {
        RequestTraceSpanLog spanLog = new RequestTraceSpanLog(
                convertTimestampMicrosToTimestampMillis(timestampMicroseconds),
                logEvent);
        addSpanLog(spanLog);

        return this;
    }

    @Override
    public io.opentracing.Span log(String key, Object value) {
        Map<String, Object> map = new HashMap();
        map.put(key, value);
        return log(map);
    }

    @Override
    public io.opentracing.Span log(long timestamp, String key, Object value) {
        Map<String, Object> map = new HashMap();
        map.put(key, value);
        return log(timestamp, map);
    }

    @Override
    public io.opentracing.Span setBaggageItem(String key, String value) {
        getSpanContext().addBaggageItem(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return getSpanContext().getBaggageItems().get(key);
    }

    @Override
    public io.opentracing.Span setOperationName(String operationName) {
        setEventName(operationName);
        return this;
    }

    @Override
    public void finish() {
        getRequestTracingServiceIfNull();
        requestTracing.traceSpan(this);
    }

    @Override
    public void finish(long finishMicros) {
        long finishMillis = convertTimestampMicrosToTimestampMillis(finishMicros);
        
        getRequestTracingServiceIfNull();
        requestTracing.traceSpan(this, finishMillis);
    }

    public void setStartTime(long startTimeMicros) {
        super.setStartInstant(Instant.ofEpochMilli(convertTimestampMicrosToTimestampMillis(startTimeMicros)));
    }

    private long convertTimestampMicrosToTimestampMillis(long timestampMicroseconds) {
        return TimeUnit.MICROSECONDS.convert(timestampMicroseconds, TimeUnit.MILLISECONDS);
    }

    private void getRequestTracingServiceIfNull() {
        if (requestTracing == null) {
            ServiceLocator serviceLocator = Globals.getDefaultBaseServiceLocator();

            if (serviceLocator != null) {
                requestTracing = serviceLocator.getService(RequestTracingService.class);
            }
        }
    }
    
    public class SpanContext extends RequestTraceSpan.SpanContext implements io.opentracing.SpanContext {

        @Override
        public Iterable<Map.Entry<String, String>> baggageItems() {
            return super.getBaggageItems().entrySet();
        }

    }
}
