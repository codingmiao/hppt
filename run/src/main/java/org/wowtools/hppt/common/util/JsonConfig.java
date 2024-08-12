package org.wowtools.hppt.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 从json型配置信息中读取需要的配置
 *
 * @author liuyu
 * @date 2024/4/8
 */
public abstract class JsonConfig {

    public static final class MapConfig extends JsonConfig {

        private final Map<String, Object> map;

        public MapConfig(Map<String, Object> map) {
            HashMap<String, Object> _map = new HashMap<>(map.size());
            map.forEach((k, v) -> {
                if (v instanceof Map<?, ?>) {
                    _map.put(k, new MapConfig((Map<String, Object>) v));
                } else if (v instanceof ArrayList<?>) {
                    _map.put(k, new ListConfig((ArrayList<Object>) v));
                } else {
                    _map.put(k, v);
                }
            });
            this.map = _map;
        }

        public <T> T value(String key) {
            return (T) map.get(key);
        }

        public <T> T value(String key, T defaultValue) {
            return (T) map.getOrDefault(key, defaultValue);
        }

        public Map<String, Object> mapValue() {
            return map;
        }

        @Override
        public MapConfig map(String key) {
            return (MapConfig) map.get(key);
        }

        @Override
        public ListConfig list(String key) {
            return (ListConfig) map.get(key);
        }
    }

    public static final class ListConfig extends JsonConfig {

        private final ArrayList<Object> list;

        public ListConfig(ArrayList<Object> list) {
            ArrayList<Object> _list = new ArrayList<>(list);
            list.forEach(v -> {
                if (v instanceof Map<?, ?>) {
                    _list.add(new MapConfig((Map<String, Object>) v));
                } else if (v instanceof ArrayList<?>) {
                    _list.add(new ListConfig((ArrayList<Object>) v));
                } else {
                    _list.add(v);
                }
            });
            this.list = _list;
        }

        public <T> T valueAt(int i) {
            return (T) list.get(i);
        }

        public ArrayList<Object> listValue() {
            return list;
        }

        @Override
        public MapConfig map(String key) {
            return null;
        }

        @Override
        public ListConfig list(String key) {
            return null;
        }
    }

    public static MapConfig newInstance(Map<String, Object> config) {
        return new MapConfig(config);
    }

    public static ListConfig newInstance(ArrayList<Object> config) {
        return new ListConfig(config);
    }


    public abstract MapConfig map(String key);

    public abstract ListConfig list(String key);
}
