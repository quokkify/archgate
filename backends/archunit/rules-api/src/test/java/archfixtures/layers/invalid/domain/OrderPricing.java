package archfixtures.layers.invalid.domain;

import archfixtures.layers.invalid.web.OrderController;

public class OrderPricing {

    public String price() {
        return new OrderController().show();
    }
}
