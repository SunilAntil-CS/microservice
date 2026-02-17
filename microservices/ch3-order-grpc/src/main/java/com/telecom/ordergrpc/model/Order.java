package com.telecom.ordergrpc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain entity: order created by business logic. We map from proto request to this,
 * then from this to proto reply. Keeps gRPC layer separate from domain.
 */
public class Order {

    private long id;
    private long restaurantId;
    private long consumerId;
    private List<LineItem> lineItems = new ArrayList<>();

    public Order() {}

    public Order(long id, long restaurantId, long consumerId, List<LineItem> lineItems) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.consumerId = consumerId;
        this.lineItems = lineItems != null ? lineItems : new ArrayList<>();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(long restaurantId) { this.restaurantId = restaurantId; }
    public long getConsumerId() { return consumerId; }
    public void setConsumerId(long consumerId) { this.consumerId = consumerId; }
    public List<LineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItem> lineItems) { this.lineItems = lineItems; }

    public static class LineItem {
        private String menuItemId;
        private int quantity;

        public LineItem() {}
        public LineItem(String menuItemId, int quantity) {
            this.menuItemId = menuItemId;
            this.quantity = quantity;
        }
        public String getMenuItemId() { return menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
