package archfixtures.deprecated;

public class ImplementsDeprecatedContract implements DeprecatedContract {

    @Override
    public String token() {
        return "token";
    }
}
