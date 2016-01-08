package de.ii.ldproxy.output.html;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author zahnen
 */
public class DatasetDTO {
    public String name;
    public String description;
    public List<String> keywords;
    public String version;
    public String license;
    public String bbox;
    public String url;
    public Set<String> featureTypes;
    public String providerName;
    public String providerUrl;
    public String contactName;
    public String contactEmail;
    public String contactTelephone;
    public String contactFaxNumber;
    public String contactHoursOfService;
    public String contactUrl;
    public String contactStreetAddress;
    public String contactLocality;
    public String contactRegion;
    public String contactPostalCode;
    public String contactCountry;

    public DatasetDTO() {
        this.keywords = new ArrayList<>();
    }
}