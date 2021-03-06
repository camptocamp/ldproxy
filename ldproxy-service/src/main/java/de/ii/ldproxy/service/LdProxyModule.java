/**
 * Copyright 2016 interactive instruments GmbH
 *
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
package de.ii.ldproxy.service;

import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.xsf.core.api.Service;
import de.ii.xsf.core.api.ServiceModule;
import de.ii.xsf.core.api.exceptions.WriteError;
import de.ii.xsf.core.api.permission.AuthenticatedUser;
import de.ii.xsf.dropwizard.api.HttpClients;
import de.ii.xsf.dropwizard.api.Jackson;
import de.ii.xsf.logging.XSFLogger;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.http.client.HttpClient;
import org.codehaus.staxmate.SMInputFactory;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
// TODO: remove
// TODO: replace this and ServiceRegistry with ServiceSuperStore and ServiceSubStores
public class LdProxyModule implements ServiceModule {

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(LdProxyModule.class);

    @Requires
    private LdProxyServiceStore serviceStore;



    @Override
    public Service addService(AuthenticatedUser authenticatedUser, String id, Map<String, String> queryParams, File configDirectory) throws IOException {
        // TODO: can we remove queryParams?
        if (queryParams.containsKey("wfsUrl")) {

            //LOGGER.info(FrameworkMessages.ADDING_WFS2GSFS_SERVICE_WITH_ID_ID_WFSURL_URL, id, queryParams.get("wfsUrl"));

            String wfsUrl = null;
            //try {
                // TODO: what does cleanWfsUrl do? move to wfs client?
                //wfsUrl = cleanWfsUrl(queryParams.get("wfsUrl"));
                wfsUrl = queryParams.get("wfsUrl");
            //} catch (URISyntaxException ex) {
                // TODO
                //LOGGER.error(FrameworkMessages.FAILED_REQUESTING_URL, queryParams.get("wfsUrl"));
                //throw new InvalidParameterValue(FrameworkMessages.FAILED_REQUESTING_URL, queryParams.get("wfsUrl"));
            //}

            //LdProxyService srvc = new LdProxyService(id, wfsUrl);
            // TODO
            //srvc.setDateCreated(new DateTime().getMillis());
            //srvc.setLastModified(srvc.getDateCreated());

            LdProxyService srvc = serviceStore.addService(id, wfsUrl);

            // TODO
            //serviceAddedPublisher.sendData(srvc);

            //LOGGER.info(FrameworkMessages.CREATED_WFS2GSFS_SERVICE_WITH_ID_ID_WFSURL_URL, id, queryParams.get("wfsUrl"));

            return srvc;

        } else {
            // TODO
            //throw new ReqiredParameterMissing(FrameworkMessages.REQUIRED_PARAMETER_PARAMNAME_IS_MISSING_IN_REQUEST, "wfsUrl");
            return null;
        }
    }

    @Override
    public Service updateService(AuthenticatedUser authenticatedUser, String id, Service update) {
        try {
            serviceStore.updateResourceOverrides(id, (LdProxyService) update);
        } catch (IOException ex) {

            // TODO Logging
            throw new WriteError();
        }

        return serviceStore.getResource(id);
    }

    @Override
    public void deleteService(AuthenticatedUser authenticatedUser, Service service) {
        try {
            // TODO: cleanup

            serviceStore.deleteResource(service.getId());
        } catch (IOException ex) {
            LOGGER.getLogger().error("Error deleting service with id {}", service.getId());
        }
    }

    @Override
    public Service getService(AuthenticatedUser authenticatedUser, String id) throws IOException {
        // TODO ...
        LdProxyService service = serviceStore.getResource(id);
        return service;
    }

    @Override
    public Map<String, List<Service>> getServices() throws IOException {
        Map<String, List<Service>> services = new HashMap<>();

        for (String id : serviceStore.getResourceIds()) {
            if (!services.containsKey(null)) {
                services.put(null, new ArrayList<Service>());
            }

            try {
                services.get(null).add(getService(null, id));
                LOGGER.getLogger().debug("Loaded Service: {}", id);
            } catch (IOException ex) {
                LOGGER.getLogger().error("Failed to load Service: {}", id);
            }
        }

        return services;
    }

    // TODO
    @Override
    public List<Service> getServiceList() throws Exception {
        LOGGER.getLogger().debug("GET SERVICE LIST 2");
        List<Service> services = new ArrayList<>();
        return services;
    }

    @Override
    public List<Service> getServiceList(AuthenticatedUser authenticatedUser) {
        LOGGER.getLogger().debug("GET SERVICE LIST");
        List<Service> srvs = new ArrayList<>();
        for (String id : serviceStore.getResourceIds()) {
            LOGGER.getLogger().debug("GET SERVICE LIST {}", id);
            srvs.add(serviceStore.getResource(id));
        }
        LOGGER.getLogger().debug("GET SERVICE LIST {}", srvs);
        return srvs;
    }

    @Override
    public String getName() {
        return "ldproxy";
    }

    @Override
    public String getDescription() {
        return "";//FrameworkMessages.MODULE_DESCRIPTION.get().toString(LOGGER.getLocale());;
    }
}
