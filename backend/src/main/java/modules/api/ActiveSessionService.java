package modules.api;

import modules.core.DjSessionController;
import org.springframework.stereotype.Service;

@Service
public class ActiveSessionService {
    private DjSessionController activeController;

    public void setActiveSession(DjSessionController activeController) {
        this.activeController = activeController;
    }

    public DjSessionController getActiveSession() {
        return activeController;
    }
}