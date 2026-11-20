package archfixtures.layers.invalid.web;

import archfixtures.layers.invalid.domain.Order;

public class OrderController {

    public String show() {
        return new Order().total();
    }
}
