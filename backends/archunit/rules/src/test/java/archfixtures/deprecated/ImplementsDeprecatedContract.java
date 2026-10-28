package archfixtures.deprecated;

@SuppressWarnings("deprecation")
public class ImplementsDeprecatedContract implements DeprecatedContract {

    @Override
    public String token() {
        return "token";
    }
}
