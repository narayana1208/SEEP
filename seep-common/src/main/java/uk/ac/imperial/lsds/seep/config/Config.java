package uk.ac.imperial.lsds.seep.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.util.Utils;

/**
 * A convenient base class for configurations to extend.
 * <p>
 * This class holds both the original configuration that was provided as well as the parsed
 */
public class Config {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /* configs for which values have been requested, used to detect unused configs */
    private final Set<String> used;

    /* the original values passed in by the user */
    private final Map<String, ?> originals;

    /* the parsed values */
    private final Map<String, Object> values;

    public Config(ConfigDef definition, Map<?, ?> originals) {
        /* check that all the keys are really strings */
        for (Object key : originals.keySet())
            if (!(key instanceof String))
                throw new ConfigException(key.toString(), originals.get(key), "Key must be a string.");
        this.originals = (Map<String, ?>) originals;
        this.values = definition.parse(this.originals);
        this.used = Collections.synchronizedSet(new HashSet<String>());
        logAll();
    }

    protected Object get(String key) {
        if (!values.containsKey(key))
            throw new ConfigException(String.format("Unknown configuration '%s'", key));
        used.add(key);
        return values.get(key);
    }

    public int getInt(String key) {
        return (Integer) get(key);
    }

    public long getLong(String key) {
        return (Long) get(key);
    }

    public double getDouble(String key) {
        return (Double) get(key);
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String key) {
        return (List<String>) get(key);
    }

    public boolean getBoolean(String key) {
        return (Boolean) get(key);
    }

    public String getString(String key) {
        return (String) get(key);
    }

    public Class<?> getClass(String key) {
        return (Class<?>) get(key);
    }

    public Set<String> unused() {
        Set<String> keys = new HashSet<String>(originals.keySet());
        keys.removeAll(used);
        return keys;
    }

    private void logAll() {
        StringBuilder b = new StringBuilder();
        b.append(getClass().getSimpleName());
        b.append(" values: ");
        b.append(Utils.NL);
        for (Map.Entry<String, Object> entry : this.values.entrySet()) {
            b.append('\t');
            b.append(entry.getKey());
            b.append(" = ");
            b.append(entry.getValue());
            b.append(Utils.NL);
        }
        log.info(b.toString());
    }

    /**
     * Log warnings for any unused configurations
     */
    public void logUnused() {
        for (String key : unused())
            log.warn("The configuration {} = {} was supplied but isn't a known config.", key, this.values.get(key));
    }

    /**
     * Get a configured instance of the give class specified by the given configuration key. If the object implements
     * Configurable configure it using the configuration.
     * 
     * @param key The configuration key for the class
     * @param t The interface the class should implement
     * @return A configured instance of the class
     */
//    public <T> T getConfiguredInstance(String key, Class<T> t) {
//        Class<?> c = getClass(key);
//        if (c == null)
//            return null;
//        Object o = Utils.newInstance(c);
//        if (!t.isInstance(o))
//            throw new SeepException(c.getName() + " is not an instance of " + t.getName());
//        if (o instanceof Configurable)
//            ((Configurable) o).configure(this.originals);
//        return t.cast(o);
//    }
//
//    public <T> List<T> getConfiguredInstances(String key, Class<T> t) {
//        List<String> klasses = getList(key);
//        List<T> objects = new ArrayList<T>();
//        for (String klass : klasses) {
//            Class<?> c = getClass(klass);
//            if (c == null)
//                return null;
//            Object o = Utils.newInstance(c);
//            if (!t.isInstance(o))
//                throw new SeepException(c.getName() + " is not an instance of " + t.getName());
//            if (o instanceof Configurable)
//                ((Configurable) o).configure(this.originals);
//            objects.add(t.cast(o));
//        }
//        return objects;
//    }

}

