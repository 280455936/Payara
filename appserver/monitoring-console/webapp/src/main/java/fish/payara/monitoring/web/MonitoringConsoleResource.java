/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.monitoring.web;

import static java.util.stream.StreamSupport.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.internal.api.Globals;

import fish.payara.monitoring.model.Series;
import fish.payara.monitoring.model.SeriesDataset;
import fish.payara.monitoring.store.MonitoringDataRepository;
import fish.payara.notification.requesttracing.RequestTrace;
import fish.payara.nucleus.requesttracing.RequestTracingService;
import fish.payara.nucleus.requesttracing.store.RequestTraceStoreInterface;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class MonitoringConsoleResource {

    private static MonitoringDataRepository getDataStore() {
        return Globals.getDefaultBaseServiceLocator().getService(MonitoringDataRepository.class);
    }

    private static RequestTraceStoreInterface getRequestTracingStore() {
        return Globals.getDefaultBaseServiceLocator().getService( RequestTracingService.class).getRequestTraceStore();
    }

    private static Series seriesOrNull(String series) {
        try {
            return new Series(series);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @GET
    @Path("/series/data/{series}/")
    public List<SeriesResponse> getSeriesData(@PathParam("series") String series) {
        Series key = seriesOrNull(series);
        return key == null ? Collections.emptyList() : SeriesResponse.from(getDataStore().selectSeries(key));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/series/data/")
    public Map<Series, List<SeriesResponse>> getSeriesData(SeriesRequest request) {
        Map<Series, List<SeriesResponse>> res = new HashMap<>();
        for (SeriesQuery query : request.queries) {
            Series key = seriesOrNull(query.series);
            List<SeriesDataset> value = key == null ? null : getDataStore().selectSeries(key, query.instances);
            if (value != null && !value.isEmpty()) {
                if (res.containsKey(key)) {
                    res.get(key).addAll(SeriesResponse.from(value));
                } else {
                    res.put(key, SeriesResponse.from(value));
                }
            } else {
                res.put(key, new ArrayList<>());
            }
        }
        return res;
    }

    @GET
    @Path("/series/")
    public String[] getSeriesNames() {
        return stream(getDataStore().selectAllSeries().spliterator(), false)
                .map(dataset -> dataset.getSeries().toString()).sorted().toArray(String[]::new);
    }

    @GET
    @Path("/instances/")
    public String[] getInstanceNames() {
        return getDataStore().instances().toArray(new String[0]);
    }

    @GET
    @Path("/trace/data/{series}/")
    public List<RequestTraceResponse> getTraceData(@PathParam("series") String series) {
        String group = series.split(""+Series.TAG_SEPARATOR)[1].substring(2);
        List<RequestTraceResponse> response = new ArrayList<>();
        for (RequestTrace trace : getRequestTracingStore().getTraces()) {
            if (RequestTracingService.metricGroupName(trace).equals(group)) {
                response.add(new RequestTraceResponse(trace));
            }
        }
        return response;
    }
}
