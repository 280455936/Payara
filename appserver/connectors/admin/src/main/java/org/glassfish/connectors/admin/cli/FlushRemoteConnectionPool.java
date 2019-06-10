/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *  Copyright (c) [2019] Payara Foundation and/or its affiliates. All rights reserved.
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
 *  file and include the License.
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
package org.glassfish.connectors.admin.cli;

import com.sun.appserv.connectors.internal.api.ConnectorConstants;
import com.sun.appserv.connectors.internal.api.ConnectorRuntime;
import com.sun.enterprise.admin.cli.cluster.LocalInstanceCommand;
import com.sun.enterprise.admin.cli.remote.RemoteCLICommand;
import com.sun.enterprise.admin.remote.RemoteRestAdminCommand;
import com.sun.enterprise.admin.remote.ServerRemoteRestAdminCommand;
import com.sun.enterprise.admin.util.RemoteInstanceCommandHelper;
import com.sun.enterprise.v3.common.PlainTextActionReporter;
import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Module;
import com.sun.enterprise.config.serverbeans.ResourceRef;
import com.sun.enterprise.config.serverbeans.Resources;;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author jonathan coustick
 * @since 5.193
 */
@Service(name = "flush-connection-pool")
@PerLookup
@TargetType(value = {CommandTarget.DOMAIN, CommandTarget.DAS})
@ExecuteOn(value = {RuntimeType.DAS})
@RestEndpoints({
    @RestEndpoint(configBean=Resources.class,
        opType=RestEndpoint.OpType.POST, 
        path="flush-connection-pool", 
        description="flush-connection-pool")
})
public class FlushRemoteConnectionPool implements AdminCommand {
    
    private static final Logger LOGGER = Logger.getLogger("org.glassfish.connectors.admin.cli");

    @Param(name = "pool_name", primary = true)
    private String poolName;

    @Param(name="appname", optional=true)
    private String applicationName;

    @Param(name="modulename", optional=true)
    private String moduleName;
    
    /**
     * There is no default, if it is not specified it against all instances known
     * if this is the DAS.
     */
    @Param(name="target", optional=true)
    private String target;

    @Inject
    private Applications applications;

    @Inject
    private ConnectionPoolUtil poolUtil;

    @Inject
    private ConnectorRuntime _runtime;
    
    @Inject
    CommandRunner commandRunner;
    
    @Inject
    private Domain domain;
    
    @Inject
    private ServiceLocator habitat;

    @Override
    public void execute(AdminCommandContext context) {
        final ActionReport report = context.getActionReport();

        Resources resources = domain.getResources();
        String scope = "";
        if(moduleName != null){
            if(!poolUtil.isValidModule(applicationName, moduleName, poolName, report)){
                report.setMessage("Modulename is not that of a valid module: " + moduleName);
                report.setActionExitCode(ActionReport.ExitCode.WARNING);
                return;
            }
            Application application = applications.getApplication(applicationName);
            Module module = application.getModule(moduleName);
            resources = module.getResources();
            scope = ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX;
        }else if(applicationName != null){
            if(!poolUtil.isValidApplication(applicationName, poolName, report)){
                report.setMessage("ApplicationName is not that of a valid module: " + applicationName);
                report.setActionExitCode(ActionReport.ExitCode.WARNING);
                return;
            }
            Application application = applications.getApplication(applicationName);
            resources = application.getResources();
            scope = ConnectorConstants.JAVA_APP_SCOPE_PREFIX;
        }

        if(!poolUtil.isValidPool(resources, poolName, scope, report)){
            report.setMessage("Connection Pool is not valid");
            report.setActionExitCode(ActionReport.ExitCode.WARNING);
            return;
        }
        
        
        for (Server server : domain.getServers().getServer()) {

            ActionReport subReport = report.addSubActionsReport();
            try {
                //there is a ref to the resource
                if (!server.isRunning()) {
                    continue;//skip servers that are stopped
                }
                String host = server.getAdminHost();
                int port = server.getAdminPort();

                ParameterMap map = new ParameterMap();
                map.add("poolName", poolName);
                if (applicationName == null) {
                    map.add("appname", applicationName);
                }
                if (moduleName != null) {
                    map.add("modulename", moduleName);
                }

                if (server.isDas()) {
                    CommandRunner runner = habitat.getService(CommandRunner.class);
                    CommandRunner.CommandInvocation invocation = runner.getCommandInvocation("_flush-connection-pool", subReport, context.getSubject());
                    invocation.parameters(map);
                    invocation.execute();
                } else {
                    RemoteRestAdminCommand rac = new ServerRemoteRestAdminCommand(habitat, "_flush-connection-pool", host, port, false, "admin", null, LOGGER);
                    rac.executeCommand(map);
                    ActionReport result = rac.getActionReport();
                    subReport.setActionExitCode(result.getActionExitCode());
                    subReport.setMessage(result.getMessage());
                }
            } catch (CommandException ex) {
                subReport.failure(Logger.getLogger("CONNECTORS-ADMIN"), ex.getLocalizedMessage(), ex);
            }

        }
        
    }
    
    
}
