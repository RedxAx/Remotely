package redxax.oxy.remotely.flow.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryNodeLoader {
    
    private final NodeRegistry registry;
    private final Map<String, NodeCategoryRegistrar> registeredCategories;
    
    public CategoryNodeLoader(NodeRegistry registry) {
        this.registry = registry;
        this.registeredCategories = new HashMap<>();
    }
    
    public void registerCategory(NodeCategoryRegistrar category) {
        String categoryName = category.getCategoryName();
        if (registeredCategories.containsKey(categoryName)) {
            System.err.println("[CategoryLoader] Category already registered: " + categoryName);
            return;
        }
        
        registeredCategories.put(categoryName, category);
        category.registerNodes(registry);
        
        System.out.println("[CategoryLoader] Registered category: " + categoryName);
    }
    
    public void registerAll(List<NodeCategoryRegistrar> categories) {
        for (NodeCategoryRegistrar category : categories) {
            registerCategory(category);
        }
    }
    
    public void unregisterCategory(String categoryName) {
        NodeCategoryRegistrar category = registeredCategories.remove(categoryName);
        if (category != null) {
            System.out.println("[CategoryLoader] Unregistered category: " + categoryName);
        }
    }
    
    public void unregisterAll() {
        List<String> categoryNames = new ArrayList<>(registeredCategories.keySet());
        for (String name : categoryNames) {
            unregisterCategory(name);
        }
    }
    
    public List<String> getRegisteredCategories() {
        return new ArrayList<>(registeredCategories.keySet());
    }
    
    public boolean isCategoryRegistered(String categoryName) {
        return registeredCategories.containsKey(categoryName);
    }
}
