package modules.userContext.services;

import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.UserContextDTO;

/**
 * Singelton Klasse, um überall auf den UserContext zugreifen zu können.
 * <p>
 * WICHTIG: Vor dem ersten Zugriff muss die UserContextStrategy gesetzt werden.
 */
public class UserContextService {
    private static UserContextService instance;
    private UserContextStrategy strategy;

    private UserContextService() {}

    public static synchronized UserContextService getInstance() {
        if (instance == null) {
            instance = new UserContextService();
        }
        return instance;
    }

    public void setStrategy(UserContextStrategy strategy) {
        this.strategy = strategy;
    }

    public UserContextDTO getCurrentContext() {
        if (this.strategy == null) {
            throw new IllegalStateException("UserContextStrategy wurde noch nicht konfiguriert!");
        }
        // Der Service delegiert die Beantwortung komplett an die Strategie
        return this.strategy.getUserContext();
    }
}