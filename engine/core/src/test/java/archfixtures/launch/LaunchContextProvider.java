package archfixtures.launch;

import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;

public final class LaunchContextProvider implements ContextProvider<LaunchContext> {

    @Override
    public Class<LaunchContext> type() {
        return LaunchContext.class;
    }

    @Override
    public LaunchContext create(ProjectLayout layout) {
        return new LaunchContext();
    }
}
