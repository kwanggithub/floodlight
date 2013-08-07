package org.projectfloodlight.db.query;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.db.util.Path;

public class SortOrder {

    public static final SortOrder EMPTY = new SortOrder();

    public enum Direction { ASCENDING, DESCENDING };

    public static class Item {

        private final Path path;
        private final Direction direction;

        public Item(Path path, Direction direction) {
            assert(path != null);
            assert(direction != null);
            this.path = path;
            this.direction = direction;
        }

        public Item(Item initItem) {
            path = new Path(initItem.getPath());
            direction = initItem.direction;
        }

        public Path getPath() {
            return path;
        }

        public Direction getDirection() {
            return direction;
        }
    }
    private final List<Item> itemList = new ArrayList<Item>();

    public SortOrder() {
    }

    public SortOrder(Path path) {
        add(path);
    }

    public SortOrder(Path path, Direction direction) {
        add(path, direction);
    }

    public SortOrder(Item item) {
        add(item);
    }

    public SortOrder(Item[] itemArray) {
        add(itemArray);
    }

    public SortOrder(List<Item> itemList) {
        add(itemList);
    }

    public SortOrder(SortOrder initSortOrder) {
        for (Item item: initSortOrder.getItemList()) {
            Item clonedItem = new Item(item);
            itemList.add(clonedItem);
        }
    }

    public void add(Path path) {
        itemList.add(new Item(path, Direction.ASCENDING));
    }

    public void add(Path path, Direction direction) {
        itemList.add(new Item(path, direction));
    }

    public void add(Item item) {
        assert(item != null);
        itemList.add(item);
    }

    public void add(Item[] itemArray) {
        for (Item item: itemArray) {
            itemList.add(item);
        }
    }

    public void add(List<Item> itemList) {
        this.itemList.addAll(itemList);
    }

    public List<Item> getItemList() {
        return itemList;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result + ((itemList == null) ? 0 : itemList.hashCode());
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
        SortOrder other = (SortOrder) obj;
        if (itemList == null) {
            if (other.itemList != null)
                return false;
        } else if (!itemList.equals(other.itemList))
            return false;
        return true;
    }

}
