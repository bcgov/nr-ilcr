package ca.bc.gov.nrs.ilcr.users;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class InMemoryUserService {

    private final ConcurrentMap<Long, UserResponse> users = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        users.put(1L, new UserResponse(1L, "ILCR Developer", "ilcr.dev@gov.bc.ca"));
        users.put(2L, new UserResponse(2L, "ILCR Admin", "ilcr.admin@gov.bc.ca"));
        users.put(3L, new UserResponse(3L, "ILCR Submitter", "ilcr.submitter@gov.bc.ca"));
    }

    public List<UserResponse> findAll() {
        return users.values().stream()
                .sorted(Comparator.comparing(UserResponse::id))
                .toList();
    }

    public Optional<UserResponse> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }
}
