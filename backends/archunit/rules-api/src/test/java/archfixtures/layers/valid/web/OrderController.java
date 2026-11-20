package archfixtures.layers.valid.web;

import archfixtures.layers.valid.domain.Order;

public class OrderController {

    public String show() {
        return new Order().total();
    }
}
