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
package de.ii.ldproxy.rest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import de.ii.ldproxy.output.geojson.GeoJsonFeatureWriter;
import de.ii.ldproxy.output.geojson.Gml2GeoJsonMapper;
import de.ii.ldproxy.output.html.*;
import de.ii.ldproxy.output.jsonld.Gml2JsonLdMapper;
import de.ii.ldproxy.output.jsonld.JsonLdOutputWriter;
import de.ii.ldproxy.service.*;
import de.ii.ogc.wfs.proxy.WfsProxyFeatureType;
import de.ii.xsf.core.api.MediaTypeCharset;
import de.ii.xsf.core.api.Service;
import de.ii.xsf.core.api.exceptions.ResourceNotFound;
import de.ii.xsf.core.api.permission.Auth;
import de.ii.xsf.core.api.permission.AuthenticatedUser;
import de.ii.xsf.core.api.permission.AuthorizationProvider;
import de.ii.xsf.core.api.rest.ServiceResource;
import de.ii.xsf.logging.XSFLogger;
import de.ii.xtraplatform.ogc.api.Versions;
import de.ii.xtraplatform.ogc.api.gml.parser.GMLAnalyzer;
import de.ii.xtraplatform.ogc.api.gml.parser.GMLParser;
import de.ii.xtraplatform.ogc.api.wfs.client.DescribeFeatureType;
import de.ii.xtraplatform.ogc.api.wfs.client.GetCapabilities;
import de.ii.xtraplatform.ogc.api.wfs.client.WFSOperation;
import de.ii.xtraplatform.ogc.api.wfs.client.WFSRequest;
import de.ii.xtraplatform.ogc.api.wfs.parser.LoggingWfsCapabilitiesAnalyzer;
import de.ii.xtraplatform.ogc.api.wfs.parser.MultiWfsCapabilitiesAnalyzer;
import de.ii.xtraplatform.ogc.api.wfs.parser.WFSCapabilitiesAnalyzer;
import de.ii.xtraplatform.ogc.api.wfs.parser.WFSCapabilitiesParser;
import io.dropwizard.views.View;
import org.apache.http.HttpEntity;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author zahnen
 */
@Produces(MediaTypeCharset.APPLICATION_JSON_UTF8)
public class LdProxyServiceResource implements ServiceResource {

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(LdProxyServiceResource.class);
    protected LdProxyService service = null;
    @Context
    protected UriInfo uriInfo;
    @Context
    protected HttpServletRequest request;

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public void setService(Service service) {
        this.service = (LdProxyService) service;
    }

    @Override
    public void init(AuthorizationProvider authorizationProvider) {

    }

    // TODO: as JSON-LD
    @GET
    @Produces(MediaTypeCharset.TEXT_HTML_UTF8)
    public View getDatasetAsHtml(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @QueryParam("token") String token) {
        DatasetView dataset = new DatasetView("service", uriInfo.getRequestUri());
        dataset.breadCrumbs = new ImmutableList.Builder<NavigationDTO>()
                .add(new NavigationDTO("Services", "../"))
                .add(new NavigationDTO(service.getName()))
                .build();

        // TODO
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        dataset.formats = new ImmutableList.Builder<NavigationDTO>()
                .add(new NavigationDTO("XML", "f=xml"))
                .build();

        for (WfsProxyFeatureType ft: service.getFeatureTypes().values()) {
            dataset.featureTypes.add(new DatasetView("service", uriInfo.getRequestUri(), ft.getName(), ft.getDisplayName()));
        }
        Collections.sort(dataset.featureTypes, new Comparator<DatasetView>() {
            @Override
            public int compare(DatasetView datasetView, DatasetView t1) {
                return datasetView.name.compareTo(t1.name);
            }
        });

        WFSOperation operation = new GetCapabilities();

        WFSCapabilitiesAnalyzer analyzer = new MultiWfsCapabilitiesAnalyzer(
                new GetCapabilities2Dataset(dataset),
                new LoggingWfsCapabilitiesAnalyzer()
        );

        WFSCapabilitiesParser wfsParser = new WFSCapabilitiesParser(analyzer, service.staxFactory);

        wfsParser.parse(service.getWfsAdapter().request(operation));

        // TODO
        dataset.url += "?";
        for (Map.Entry<String,String> p :operation.getGETParameters(null, new Versions()).entrySet()) {
            dataset.url += p.getKey() + "=" + p.getValue() + "&";
        }

        return dataset;
    }

    @GET
    //@JsonView(JsonViews.GSFSView.class)
    @Produces("application/xml;charset=utf-8")
    public Response getDatasetAsXml(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @QueryParam("callback") String callback) {
        return getWfsCapabilities();
    }



    /*@Path("/{layerid}/")
    @GET
    //@JsonView(JsonViews.GSFSView.class)
    @Produces("application/xml;charset=utf-8")
    public Response getServiceLayerGET(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @QueryParam("callback") String callback) {

        return getWfsDescribeFeatureType(layerid);
    }*/

    @Path("/{layerid}")
    @GET
    @Produces(MediaTypeCharset.TEXT_HTML_UTF8)
    public Response getFeaturesAsHtml(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @QueryParam("fields") String fields, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET HTML FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }
        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        DatasetView dataset = new DatasetView("", uriInfo.getRequestUri());
        FeatureCollectionView featureTypeDataset = new FeatureCollectionView("featureCollection", uriInfo.getRequestUri(), featureType.getName(), featureType.getDisplayName());
        dataset.featureTypes.add(featureTypeDataset);

        featureTypeDataset.breadCrumbs = new ImmutableList.Builder<NavigationDTO>()
                .add(new NavigationDTO("Services", "../../"))
                .add(new NavigationDTO(service.getName(), "../../" + service.getBrowseUrl()))
                .add(new NavigationDTO(featureType.getDisplayName()))
                .build();

        ImmutableList.Builder<NavigationDTO> indices = new ImmutableList.Builder<NavigationDTO>();
        for(String index: service.findIndicesForFeatureType(featureType).keySet()) {
            indices.add(new NavigationDTO(index, "?fields=" + index + "&distinctValues=true"));
        }
        featureTypeDataset.indices = indices.build();

        WFSOperation operation = new GetCapabilities();

        WFSCapabilitiesAnalyzer analyzer = new MultiWfsCapabilitiesAnalyzer(
                new GetCapabilities2Dataset(dataset),
                new LoggingWfsCapabilitiesAnalyzer()
        );

        WFSCapabilitiesParser wfsParser = new WFSCapabilitiesParser(analyzer, service.staxFactory);

        wfsParser.parse(service.getWfsAdapter().request(operation));




        List<String> groupings = new ArrayList<>();
        if (layerid.equals("inspireadressen")) {
            groupings.add("woonplaats");
        }

        String query = request.getQueryString() == null ? "" : request.getQueryString();

        featureTypeDataset.formats = new ImmutableList.Builder<NavigationDTO>()
                .add(new NavigationDTO("JSON-LD", "f=jsonld"))
                .add(new NavigationDTO("GeoJson", "f=json"))
                .add(new NavigationDTO("GML", "f=xml"))
                .build();

        // TODO: split fields
        if (fields != null && service.findIndicesForFeatureType(featureType).containsKey(fields)) {

            featureTypeDataset.breadCrumbs = new ImmutableList.Builder<NavigationDTO>()
                    .add(new NavigationDTO("Services", "../../"))
                    .add(new NavigationDTO(service.getName(), "../../" + service.getBrowseUrl()))
                    .add(new NavigationDTO(featureType.getDisplayName(), "./"))
                    .add(new NavigationDTO(fields))
                    .build();

            featureTypeDataset.formats = new ImmutableList.Builder<NavigationDTO>()
                    .build();

            featureTypeDataset.hideMetadata = true;
            featureTypeDataset.hideMap = true;
            featureTypeDataset.index = fields;
            List<String> values = service.getIndexValues(featureType, fields, service.findIndicesForFeatureType(featureType).get(fields));
            for (int i = 0; i < values.size(); i++) {
                FeatureDTO currentFeature = new FeatureDTO();
                currentFeature.name = values.get(i);
                currentFeature.id = new FeaturePropertyDTO();
                currentFeature.id.value = "?" + fields + "=" + values.get(i);
                currentFeature.noUrlClosingSlash = true;
                featureTypeDataset.features.add(currentFeature);
            }

            return Response.ok(featureTypeDataset).build();
        }

        for (String filterKey: uriInfo.getQueryParameters().keySet()) {
            if (service.findIndicesForFeatureType(featureType, false).containsKey(filterKey)) {

                // + TODO: same for postalCode
                // + check template url + isPartOf TODO: schema.org mapping of aggregations and links
                // + TODO: harvest all values
                // TODO: links in json-ld
                // TODO: view as for aggregation sites

                String filterValue = uriInfo.getQueryParameters().getFirst(filterKey);

                if (service.findIndicesForFeatureType(featureType).containsKey(filterKey)) {
                    featureTypeDataset.breadCrumbs = new ImmutableList.Builder<NavigationDTO>()
                            .add(new NavigationDTO("Services", "../../"))
                            .add(new NavigationDTO(service.getName(), "../../" + service.getBrowseUrl()))
                            .add(new NavigationDTO(featureType.getDisplayName(), "./"))
                            .add(new NavigationDTO(filterKey, "./?fields=" + filterKey + "&distinctValues=true"))
                            .add(new NavigationDTO(filterValue))
                            .build();
                } else {
                    featureTypeDataset.breadCrumbs = new ImmutableList.Builder<NavigationDTO>()
                            .add(new NavigationDTO("Services", "../../"))
                            .add(new NavigationDTO(service.getName(), "../../" + service.getBrowseUrl()))
                            .add(new NavigationDTO(featureType.getDisplayName(), "./"))
                            .add(new NavigationDTO(filterKey))
                            .add(new NavigationDTO(filterValue))
                            .build();
                }

                featureTypeDataset.hideMetadata = true;
                featureTypeDataset.index = filterKey;
                featureTypeDataset.indexValue = filterValue;

                Map<String,String> announcements = null;
                if (filterKey.equals("addressLocality")) {
                    announcements = service.getSparqlAdapter().request(filterValue, SparqlAdapter.QUERY.ADDRESS_LOCALITY);
                } else if (filterKey.equals("postalCode")) {
                    announcements = service.getSparqlAdapter().request(filterValue, SparqlAdapter.QUERY.POSTAL_CODE);
                }

                // TODO
                if (announcements != null && !announcements.isEmpty()) {
                    featureTypeDataset.links = new FeaturePropertyDTO();
                    featureTypeDataset.links.name = "Announcements";
                    for (Map.Entry<String,String> id: announcements.entrySet()) {
                        FeaturePropertyDTO link = new FeaturePropertyDTO();
                        link.value = id.getKey();
                        link.name = makeDisplayName(id.getKey(), id.getValue());
                        link.itemProp = id.getValue();
                        featureTypeDataset.links.addChild(link);
                    }
                }

                return getHtmlResponse(getWfsFeaturesPaged(layerid, range, service.findIndicesForFeatureType(featureType, false).get(filterKey), filterValue), featureType, true, groupings, false, query, parseRange(range), featureTypeDataset);
            }
        }
        //if (indexId.toLowerCase().equals("all")) {
        return getHtmlResponse(getWfsFeaturesPaged(layerid, range), featureType, true, groupings, false, query, parseRange(range), featureTypeDataset);
        //} else {
        //    return getWfsPropertiesPaged(layerid, range, indexId);
        //}
    }

    private String makeDisplayName(String id, String title) {
        if (title.length() > 100) {
            title = title.substring(0,99) + " ...";
        }
        return title + " (" + id.substring(id.lastIndexOf('/')+1) + ")";
    }

    @Path("/{layerid}")
    @GET
    public Response getFeaturesAsJson(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET JSON FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }
        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        for (String filterKey: uriInfo.getQueryParameters().keySet()) {
            if (service.findIndicesForFeatureType(featureType, false).containsKey(filterKey)) {
                String filterValue = uriInfo.getQueryParameters().getFirst(filterKey);
                return getJsonResponse(getWfsFeaturesPaged(layerid, range, service.findIndicesForFeatureType(featureType, false).get(filterKey), filterValue), featureType, true);
            }
        }

        //if (indexId.toLowerCase().equals("all")) {
            return getJsonResponse(getWfsFeaturesPaged(layerid, range), featureType, true);
        //} else {
        //    return getWfsPropertiesPaged(layerid, range, indexId);
        //}
    }

    @Path("/{layerid}")
    @GET
    @Produces("application/ld+json;charset=utf-8")
    public Response getFeaturesAsJsonLd(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET JSON-LD FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }
        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        DatasetView dataset = new DatasetView("", uriInfo.getRequestUri());
        FeatureCollectionView featureTypeDataset = new FeatureCollectionView("featureCollection", uriInfo.getRequestUri(), featureType.getName(), featureType.getDisplayName());
        dataset.featureTypes.add(featureTypeDataset);

        WFSOperation operation = new GetCapabilities();

        WFSCapabilitiesAnalyzer analyzer = new MultiWfsCapabilitiesAnalyzer(
                new GetCapabilities2Dataset(dataset),
                new LoggingWfsCapabilitiesAnalyzer()
        );

        WFSCapabilitiesParser wfsParser = new WFSCapabilitiesParser(analyzer, service.staxFactory);

        wfsParser.parse(service.getWfsAdapter().request(operation));

        for (String filterKey: uriInfo.getQueryParameters().keySet()) {
            if (service.findIndicesForFeatureType(featureType, false).containsKey(filterKey)) {
                String filterValue = uriInfo.getQueryParameters().getFirst(filterKey);

                featureTypeDataset.hideMetadata = true;
                featureTypeDataset.index = filterKey;
                featureTypeDataset.indexValue = filterValue;

                Map<String,String> announcements = null;
                if (filterKey.equals("addressLocality")) {
                    announcements = service.getSparqlAdapter().request(filterValue, SparqlAdapter.QUERY.ADDRESS_LOCALITY);
                } else if (filterKey.equals("postalCode")) {
                    announcements = service.getSparqlAdapter().request(filterValue, SparqlAdapter.QUERY.POSTAL_CODE);
                }

                // TODO
                if (announcements != null && !announcements.isEmpty()) {
                    featureTypeDataset.links = new FeaturePropertyDTO();
                    featureTypeDataset.links.name = "Announcements";
                    for (Map.Entry<String,String> id: announcements.entrySet()) {
                        FeaturePropertyDTO link = new FeaturePropertyDTO();
                        link.value = id.getKey();
                        link.name = makeDisplayName(id.getKey(), id.getValue());
                        link.itemProp = id.getValue();
                        featureTypeDataset.links.addChild(link);
                    }
                }

                return getJsonLdResponse(getWfsFeaturesPaged(layerid, range, service.findIndicesForFeatureType(featureType, false).get(filterKey), filterValue), featureType, true, featureTypeDataset);
            }
        }

        //if (indexId.toLowerCase().equals("all")) {
        return getJsonLdResponse(getWfsFeaturesPaged(layerid, range), featureType, true, featureTypeDataset);
        //} else {
        //    return getWfsPropertiesPaged(layerid, range, indexId);
        //}
    }

    @Path("/{layerid}")
    @GET
    @Produces("application/xml;charset=utf-8")
    public Response getFeaturesAsXml(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @QueryParam("properties") String fields, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET JSON FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }
        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        for (String filterKey: uriInfo.getQueryParameters().keySet()) {
            if (service.findIndicesForFeatureType(featureType, false).containsKey(filterKey)) {
                String filterValue = uriInfo.getQueryParameters().getFirst(filterKey);
                return getWfsResponse(getWfsFeaturesPaged(layerid, range, service.findIndicesForFeatureType(featureType, false).get(filterKey), filterValue));
            }
        }

        //if (indexId.toLowerCase().equals("all")) {
        return getWfsResponse(getWfsFeaturesPaged(layerid, range));
        //} else {
        //    return getWfsPropertiesPaged(layerid, range, indexId);
        //}
    }

    @Path("/{layerid}/{featureid}")
    @GET
    public Response getFeatureByIdAsJson(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @PathParam("featureid") final String featureid, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET JSON FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }

        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        //if (indexId.toLowerCase().equals("all")) {
        return getJsonResponse(getWfsFeatureById(layerid, featureid), featureType, false);
        //} else {
        //    return getWfsFeaturesPaged(layerid, range, indexId, featureid);
        //}
    }

    @Path("/{layerid}/{featureid}")
    @GET
    @Produces("application/ld+json;charset=utf-8")
    public Response getFeatureByIdAsJsonLd(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @PathParam("featureid") final String featureid, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET JSON-LD FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }

        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        //if (indexId.toLowerCase().equals("all")) {
        return getJsonLdResponse(getWfsFeatureById(layerid, featureid), featureType, false, null);
        //} else {
        //    return getWfsFeaturesPaged(layerid, range, indexId, featureid);
        //}
    }

    @Path("/{layerid}/{featureid}")
    @GET
    @Produces("application/xml;charset=utf-8")
    public Response getFeatureByIdAsAXml(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @PathParam("featureid") final String featureid, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {

        //if (indexId.toLowerCase().equals("all")) {
        return getWfsResponse(getWfsFeatureById(layerid, featureid));
        //} else {
        //    return getWfsFeaturesPaged(layerid, range, indexId, featureid);
        //}
    }

    @Path("/{layerid}/{featureid}")
    @GET
    @Produces(MediaTypeCharset.TEXT_HTML_UTF8)
    public Response getFeatureByIdAsHtml(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @PathParam("featureid") final String featureid, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {
        // TODO
        String[] ft = parseLayerId(layerid);
        String featureTypeName = service.getWfsAdapter().getNsStore().getNamespaceURI(ft[0]) + ":" + ft[1];
        LOGGER.getLogger().debug("GET HTML FOR {}", featureTypeName);
        if (!service.getFeatureTypes().containsKey(featureTypeName)) {
            throw new ResourceNotFound();
        }

        WfsProxyFeatureType featureType = service.getFeatureTypes().get(featureTypeName);

        FeatureCollectionView featureTypeDataset = new FeatureCollectionView("", uriInfo.getRequestUri(), featureType.getName(), featureType.getDisplayName());
        featureTypeDataset.description = featureType.getDisplayName();

        featureTypeDataset.breadCrumbs = new ImmutableList.Builder<NavigationDTO>()
                .add(new NavigationDTO("Services", "../../../"))
                .add(new NavigationDTO(service.getName(), "../../../" + service.getBrowseUrl()))
                .add(new NavigationDTO(featureType.getDisplayName(), "../../../" + service.getBrowseUrl() + layerid + "/"))
                .add(new NavigationDTO(featureid))
                .build();

        featureTypeDataset.formats = new ImmutableList.Builder<NavigationDTO>()
                .add(new NavigationDTO("JSON-LD", "f=jsonld"))
                .add(new NavigationDTO("GeoJson", "f=json"))
                .add(new NavigationDTO("GML", "f=xml"))
                .build();



        //if (indexId.toLowerCase().equals("all")) {
        return getHtmlResponse(getWfsFeatureById(layerid, featureid), featureType, false, new ArrayList<String>(), false, "", null, featureTypeDataset);
        //} else {
        //    return getWfsFeaturesPaged(layerid, range, indexId, featureid);
        //}
    }

    /*@Path("/{layerid}/{indexId}/{featureid}/{featureid2}")
    @GET
    @Produces("application/xml;charset=utf-8")
    public Response getServiceLayerFeature2GET(@Auth(protectedResource = true, exceptions = "arcgis") AuthenticatedUser user, @PathParam("layerid") String layerid, @PathParam("indexId") String indexId, @PathParam("featureid") final String featureid, @PathParam("featureid2") final String featureid2, @QueryParam("callback") String callback, @HeaderParam("Range") String range) {

        // TODO: filter, error handling
            return getWfsResponse(getWfsFeatureById(layerid, featureid2));
    }*/




    private WFSOperation getWfsFeaturesPaged(String layerId, String range, String indexId, String indexValue) {
        String[] ft = parseLayerId(layerId);
        int[] countFrom = parseRange(range);
        int count = countFrom[0];
        int startIndex = countFrom[1];

        return new GetFeaturePaging(ft[0], ft[1], count, startIndex, indexId.substring(indexId.lastIndexOf(':')+1), indexValue);
    }

    private WFSOperation getWfsFeaturesPaged(String layerId, String range) {
        String[] ft = parseLayerId(layerId);
        int[] countFrom = parseRange(range);
        int count = countFrom[0];
        int startIndex = countFrom[1];

        return new GetFeaturePaging(ft[0], ft[1], count, startIndex);
    }

    private WFSOperation getWfsFeatureById(String layerId, String featureId) {
        String[] ft = parseLayerId(layerId);
        return new GetFeatureById(ft[0], ft[1], featureId);
    }

    private Response getWfsDescribeFeatureType(String layerId) {
        String[] ft = parseLayerId(layerId);

        Map<String, List<String>> fts = new ImmutableMap.Builder<String, List<String>>().put(ft[0], Lists.newArrayList(ft[1])).build();
        LOGGER.getLogger().debug("GET LAYER {}", fts);
        WFSOperation operation = new DescribeFeatureType(fts);

        return getWfsResponse(operation);
    }

    private Response getWfsCapabilities() {
        WFSOperation operation = new GetCapabilities();

        return getWfsResponse(operation);
    }

    private WFSOperation getWfsPropertiesPaged(String layerId, String range, String indexId) {
        String[] ft = parseLayerId(layerId);
        int[] countFrom = parseRange(range);
        int count = countFrom[0];
        int startIndex = countFrom[1];

        return new GetPropertyValuePaging(ft[0], ft[1], indexId, count, startIndex);
    }

    private Response getWfsResponse(final WFSOperation operation) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                HttpEntity res = ((LdProxyService)service).getWfsAdapter().request(operation);
                res.writeTo(output);
            }
        };

        return Response.ok().entity(stream).build();
    }

    private Response getJsonResponse(final WFSOperation operation, final WfsProxyFeatureType featureType, final boolean isFeatureCollection) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                //HttpEntity res = ((LdProxyService)service).getWfsAdapter().request(operation);
                //res.writeTo(output);
                WFSRequest request = new WFSRequest(service.getWfsAdapter(), operation);
                try {

                    GMLAnalyzer jsonWriter = new GeoJsonFeatureWriter(service.createJsonGenerator(output), service.jsonMapper, isFeatureCollection, featureType.getMappings(), Gml2GeoJsonMapper.MIME_TYPE, service.getCrsTransformations().getDefaultTransformer());
                    GMLParser gmlParser = new GMLParser(jsonWriter, service.staxFactory);
                    gmlParser.parse(request.getResponse(), featureType.getNamespace(), featureType.getName());

                } catch (ExecutionException ex) {

                }
            }
        };

        return Response.ok().entity(stream).build();
    }

    private Response getJsonLdResponse(final WFSOperation operation, final WfsProxyFeatureType featureType, final boolean isFeatureCollection, final FeatureCollectionView dataset) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                //HttpEntity res = ((LdProxyService)service).getWfsAdapter().request(operation);
                //res.writeTo(output);
                WFSRequest request = new WFSRequest(service.getWfsAdapter(), operation);
                try {

                    GMLAnalyzer jsonWriter = new JsonLdOutputWriter(service.createJsonGenerator(output), service.jsonMapper, isFeatureCollection, featureType.getMappings(), Gml2JsonLdMapper.MIME_TYPE, service.getCrsTransformations().getDefaultTransformer(), uriInfo.getRequestUri(), dataset, service.getRewrites(), service.getVocab());
                    GMLParser gmlParser = new GMLParser(jsonWriter, service.staxFactory);
                    gmlParser.parse(request.getResponse(), featureType.getNamespace(), featureType.getName());

                } catch (ExecutionException ex) {

                }
            }
        };

        return Response.ok().entity(stream).build();
    }

    private Response getHtmlResponse(final WFSOperation operation, final WfsProxyFeatureType featureType, final boolean isFeatureCollection, final List<String> groupings, final boolean group, final String query, final int[] range, final FeatureCollectionView featureTypeDataset) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {

                WFSRequest request = new WFSRequest(service.getWfsAdapter(), operation);

                if (featureTypeDataset != null) {
                    featureTypeDataset.requestUrl = request.getAsUrl();
                }
                try {
                    GMLAnalyzer htmlWriter = new MicrodataFeatureWriter(new OutputStreamWriter(output), featureType.getMappings(), Gml2MicrodataMapper.MIME_TYPE, isFeatureCollection, featureType.getName().equals("inspireadressen"), groupings, group, query, range, featureTypeDataset, service.getCrsTransformations().getDefaultTransformer(), service.getSparqlAdapter());
                    GMLParser gmlParser = new GMLParser(htmlWriter, service.staxFactory);
                    gmlParser.parse(request.getResponse(), featureType.getNamespace(), featureType.getName());

                } catch (ExecutionException ex) {
                    // ignore
                }
            }
        };

        return Response.ok().entity(stream).build();
    }

    private String[] parseLayerId(String layerId) {
        String[] ft = layerId.split(":");
        LOGGER.getLogger().debug("LAYER {}", (Object)ft);
        if (ft.length == 1) {
            for (WfsProxyFeatureType featureType: service.getFeatureTypes().values()) {
                if (ft[0].equals(featureType.getName())) {
                    String[] ft2 = {service.getWfsAdapter().getNsStore().getNamespacePrefix(featureType.getNamespace()), ft[0]};
                    return ft2;
                }
            }
            String[] ft2 = {ft[0], ft[0]};
            return ft2;
        }
        return ft;
    }

    private int[] parseRange(final String range) {
        LOGGER.getLogger().debug("RANGE {}", range);
        // TODO: get from config
        int PAGE_SIZE = 25;
        int from = 0;
        int to = PAGE_SIZE;
        int page = 1;
        if (range != null) {
            try {
                String[] ranges = range.split("=")[1].split("-");
                from = Integer.parseInt(ranges[0]);
                to = Integer.parseInt(ranges[1]);
                page = to / PAGE_SIZE;
            } catch (NumberFormatException ex) {
                //ignore
            }
        }
        int[] countFrom = {to-from, from, page, PAGE_SIZE};

        return countFrom;

        // TODO
        // Accept-Ranges: items
        // 206 Partial Content
        // 416 Range Not Satisfiable

        // TODO: get from wfs response
        /*String responseRange = String.format("items=%d-%d/%d", from, result.getHits(), result.getTotalHits());

        return Response.ok(result.getResults())
                .header("Content-Range", responseRange)
                .build();
        */
    }
}
