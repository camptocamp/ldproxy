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

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.ii.ldproxy.output.geojson.GeoJsonFeatureWriter;
import de.ii.ldproxy.output.geojson.Gml2GeoJsonMapper;
import de.ii.ldproxy.output.html.Gml2MicrodataMapper;
import de.ii.ldproxy.output.jsonld.Gml2JsonLdMapper;
import de.ii.ogc.wfs.proxy.AbstractWfsProxyService;
import de.ii.ogc.wfs.proxy.TargetMapping;
import de.ii.ogc.wfs.proxy.WfsProxyFeatureType;
import de.ii.xsf.logging.XSFLogger;
import de.ii.xtraplatform.ogc.api.WFS;
import de.ii.xtraplatform.ogc.api.gml.parser.GMLAnalyzer;
import de.ii.xtraplatform.ogc.api.gml.parser.GMLParser;
import de.ii.xtraplatform.ogc.api.wfs.client.WFSAdapter;
import de.ii.xtraplatform.ogc.api.wfs.client.WFSOperation;
import de.ii.xtraplatform.ogc.api.wfs.client.WFSRequest;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * @author zahnen
 */
public class LdProxyService extends AbstractWfsProxyService {

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(LdProxyService.class);
    public static final String SERVICE_TYPE = "ldproxy";
    private static final String INTERFACE_SPECIFICATION = "LinkedDataService";

    //private Map<String, List<String>> indices;
    private LdProxyIndexStore indexStore;
    private SparqlAdapter sparqlAdapter;

    // TODO: we already have external url, can we use it here?
    private Map<String, String> rewrites;

    private String vocab;


    public LdProxyService() {
        this.rewrites = new HashMap<>();
    }

    public LdProxyService(String id, String wfsUrl) {
        super(id, SERVICE_TYPE, null, new WFSAdapter(wfsUrl.trim()));

        this.rewrites = new HashMap<>();
                //this.description = "";
        //String[] path = {orgid};
        //initialize(path, module);

        // TODO: dynamic
        this.schemaAnalyzers.add(new Gml2GeoJsonMapper(this));
        this.schemaAnalyzers.add(new Gml2MicrodataMapper(this));
        this.schemaAnalyzers.add(new Gml2JsonLdMapper(this));

        // TODO
        //this.analyzeWFS();
    }

    public final void initialize(LdProxyIndexStore indexStore, SparqlAdapter sparqlAdapter) {
        this.indexStore = indexStore;
        this.sparqlAdapter = sparqlAdapter;
    }

    @Override
    public String getInterfaceSpecification() {
        return INTERFACE_SPECIFICATION;
    }

    public Map<String, String> getRewrites() {
        return rewrites;
    }

    public void setRewrites(Map<String, String> rewrites) {
        this.rewrites = rewrites;
    }

    public Map<String, String> findIndicesForFeatureType(WfsProxyFeatureType ft) {
        return  findIndicesForFeatureType(ft, true);
    }

    public Map<String, String> findIndicesForFeatureType(WfsProxyFeatureType ft, boolean onlyEnabled) {
        Map<String, String> indices = new HashMap<>();

        Map<String, List<TargetMapping>> mappings = ft.getMappings().findMappings(IndexMapping.MIME_TYPE);
        for(String path: mappings.keySet()) {
            for (TargetMapping mapping: mappings.get(path)) {
                if (!onlyEnabled || mapping.isEnabled()) {
                    indices.put(mapping.getName(), path);
                }
            }
        }

        return indices;
    }

    @JsonIgnore
    public List<String> getIndexValues(WfsProxyFeatureType featureType, String index, String property) {
        List<String> values = new ArrayList<>();

        if(findIndicesForFeatureType(featureType).containsKey(index)) {
            if (!indexStore.hasResource(index)) {
                try {
                    values.addAll(harvestIndex(featureType, property));

                    PropertyIndex propertyIndex = new PropertyIndex();
                    propertyIndex.setResourceId(index);
                    propertyIndex.setValues(values);

                    indexStore.addResource(propertyIndex);
                } catch (ExecutionException | IOException e) {
                    LOGGER.getLogger().debug("Error harvesting index", e);
                }
            } else {
                return indexStore.getResource(index).getValues();
            }
        }

        return values;
    }

    private SortedSet<String> harvestIndex(WfsProxyFeatureType featureType, String property) throws ExecutionException {
        // TODO: only if WFS 2.0.0, else GetFeature
        // TODO: seems to be incomplete, try GetFeature with paging

        String propertyName = property.substring(property.lastIndexOf(':')+1);

        SortedSet<String> values = new TreeSet<>();
        int count = 15000;
        int startIndex = 0;
        int numberMatched = 1;
        int tries = 0;

        //WFSOperation operation = new GetPropertyValuePaging(getWfsAdapter().getNsStore().getNamespacePrefix(featureType.getNamespace()), featureType.getName(), propertyName, -1, 0);
        while (numberMatched > 0 && startIndex < numberMatched) {
            WFSOperation operation = new GetFeaturePaging(getWfsAdapter().getNsStore().getNamespacePrefix(featureType.getNamespace()), featureType.getName(), count, startIndex);
            WFSRequest request = new WFSRequest(getWfsAdapter(), operation);

            IndexValueWriter indexValueWriter = new IndexValueWriter(propertyName);

            GMLParser gmlParser = new GMLParser(indexValueWriter, staxFactory);
            try {
                gmlParser.parse(request.getResponse(), WFS.getNS(WFS.VERSION._2_0_0), "member");
            } catch(Throwable e) {
                // ignore
            }
            if(indexValueWriter.hasFailed() && tries < 3) {
                tries++;
                LOGGER.getLogger().debug("TRYING AGAIN {}", tries);
                continue;
            }

            values.addAll(indexValueWriter.getValues());
            numberMatched = indexValueWriter.getNumberMatched();
            startIndex += count;
            tries = 0;
            LOGGER.getLogger().debug("{}/{}", startIndex, numberMatched);
        }

        return values;
    }

    public SparqlAdapter getSparqlAdapter() {
        return sparqlAdapter;
    }

    public String getVocab() {
        return vocab;
    }

    public void setVocab(String vocab) {
        this.vocab = vocab;
    }
}
