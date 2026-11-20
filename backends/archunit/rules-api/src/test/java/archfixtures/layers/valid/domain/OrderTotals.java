package archfixtures.layers.valid.domain;

public class OrderTotals {

    public String sum() {
        return new Order().total();
    }
}
