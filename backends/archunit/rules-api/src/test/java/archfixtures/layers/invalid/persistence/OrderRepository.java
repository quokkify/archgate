package archfixtures.layers.invalid.persistence;

import archfixtures.layers.invalid.domain.Order;

public class OrderRepository {

    public Order load() {
        return new Order();
    }
}
