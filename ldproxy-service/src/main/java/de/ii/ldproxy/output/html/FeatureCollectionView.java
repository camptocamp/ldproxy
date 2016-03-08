package de.ii.ldproxy.output.html;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zahnen
 */
public class FeatureCollectionView extends DatasetView {

    public String requestUrl;
    public List<NavigationDTO> pagination;
    public List<NavigationDTO> metaPagination;
    public List<FeatureDTO> features;
    public List<NavigationDTO> indices;
    public String index;
    public String indexValue;
    public boolean hideMap;
    public boolean hideMetadata;
    public FeaturePropertyDTO links;

    public FeatureCollectionView(String template, URI uri) {
        super(template, uri);
        this.features = new ArrayList<>();
    }

    public FeatureCollectionView(String template, URI uri, String name) {
        super(template, uri, name);
        this.features = new ArrayList<>();
    }

    public FeatureCollectionView(String template, URI uri, String name, String title) {
        super(template, uri, name, title);
        this.features = new ArrayList<>();
    }

    public String getQueryWithoutPage() {
        String query = getQuery();
        if (!query.contains("page=")) {
            return query;
        }
        return query.substring(0, query.lastIndexOf("page="));
    }
}
