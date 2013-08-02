package net.bigdb.util;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Representation of a path to a node in a tree.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public final class Path implements java.lang.Iterable<String> {

    private static final char DEFAULT_SEPARATOR = '/';

    public static final Path ROOT_PATH = new Path("/");
    public static final Path EMPTY_PATH = new Path("");
    
    public enum Type {ABSOLUTE, RELATIVE};
    
    private final Type type;
    private final char separator;
    private final List<String> components = new ArrayList<String>();
    
    public Path(String path) {
        this(path, DEFAULT_SEPARATOR);
    }
    
    public Path(String path, char separator) {
        path = path.trim();
        int length = path.length();
        int start = 0;
        if ((length > 0) && (path.charAt(0) == separator)) {
            type = Type.ABSOLUTE;
            start++;
        } else {
            type = Type.RELATIVE;
        }
        while (start < length) {
            int end = path.indexOf(separator, start);
            if (end < 0)
                end = length;
            String component = path.substring(start, end);
            components.add(component);
            start = end + 1;
        }
        this.separator = separator;
    }
    
    public Path(Type type, List<String> components, char separator) {
        this.type = type;
        this.components.addAll(components);
        this.separator = separator;
    }
    
    public Path(Type type) {
        this(type, Collections.<String>emptyList());
    }

    public Path(Type type, List<String> components) {
        this(type, components, DEFAULT_SEPARATOR);
    }

    public Path(Path... paths) {
        if (paths.length == 0) {
            this.type = Type.RELATIVE;
            this.separator = DEFAULT_SEPARATOR;
        } else {
            Path first = paths[0];
            this.type = first.type;
            this.separator = first.separator;
            for (Path path: paths) {
                this.components.addAll(path.components);
            }
        }
    }

    public Type getType() {
        return type;
    }
    
    public List<String> getComponents() {
        return components;
    }

    public char getSeparator() {
        return separator;
    }

    public int size() {
        return components.size();
    }
    
    public String get(int n) {
        return components.get(n);
    }
    
    public void add(String component) {
        components.add(component);
    }
    
    public void remove(int n) {
        components.remove(n);
    }
    
    public Path getSubPath(int start, int end) {
        assert start >= 0;
        assert end <= size();
        assert start <= end;

        Path.Type subPathType = (type == Type.ABSOLUTE) && (start == 0) ?
                Type.ABSOLUTE : Type.RELATIVE;

        return new Path(subPathType, components.subList(start, end), separator);
    }

    public Path getSubPath(int start) {
        return getSubPath(start, size());
    }

    public Path getChildPath(String childPathString) {
        return getChildPath(new Path(childPathString));
    }

    public Path getChildPath(Path childPath) {
        return new Path(this, childPath);
    }

    @Override
    public Iterator<String> iterator() {
        return components.iterator();
    }
    
    /**
     * This utility function strips off a leading or trailing slash in a
     * path string. 
     * @param path
     * @return
     */
    public static String cleanPathString(String path) {
        if (path == null)
            return null;

        boolean startsWithSlash = path.startsWith("/");
        boolean endsWithSlash = path.endsWith("/");
        
        if (!startsWithSlash && !endsWithSlash)
            return path;
        
        int startIndex = startsWithSlash ? 1 : 0;
        int endIndex = path.length();
        if (endsWithSlash)
            endIndex--;
        
        return path.substring(startIndex, endIndex);
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (type == Type.ABSOLUTE)
            builder.append(separator);
        boolean addSeparator = false;
        for (String component: components) {
            if (addSeparator)
                builder.append(separator);
            addSeparator = true;
            builder.append(component);
        }
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result +
                        ((components == null) ? 0 : components.hashCode());
        result = prime * result + separator;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Path other = (Path) obj;
        if (components == null) {
            if (other.components != null)
                return false;
        } else if (!components.equals(other.components))
            return false;
        if (separator != other.separator)
            return false;
        if (type != other.type)
            return false;
        return true;
    }
}
