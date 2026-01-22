package redxax.oxy.remotely.flow.registry;

public interface NodeCategoryRegistrar {
    
    void registerNodes(NodeRegistry registry);
    
    default String getCategoryName() {
        return this.getClass().getSimpleName();
    }
}
