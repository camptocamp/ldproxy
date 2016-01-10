package de.ii.ldproxy.output.html;

import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.jackson.dynamic.JacksonSubTypeIds;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.util.Map;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class MicrodataMappingSubTypeIds implements JacksonSubTypeIds {
    @Override
    public Map<Class<?>, String> getMapping() {
        return new ImmutableMap.Builder<Class<?>, String>()
                .put(MicrodataPropertyMapping.class, "MICRODATA_PROPERTY")
                .put(MicrodataGeometryMapping.class, "MICRODATA_GEOMETRY")
                .build();
    }
}