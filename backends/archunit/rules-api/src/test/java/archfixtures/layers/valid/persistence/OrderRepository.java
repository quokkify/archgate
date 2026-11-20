package archfixtures.layers.valid.persistence;

import archfixtures.layers.valid.domain.Order;

public class OrderRepository {

    public Order load() {
        return new Order();
    }
}
