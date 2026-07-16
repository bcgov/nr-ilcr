package ca.bc.gov.nrs.ilcr.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InMemoryUserService service = new InMemoryUserService();
        service.seed();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(service))
                .build();
    }

    @Test
    void findAllReturnsSeededUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("ILCR Developer")))
                .andExpect(jsonPath("$[1].name", is("ILCR Admin")))
                .andExpect(jsonPath("$[2].name", is("ILCR Submitter")));
    }

    @Test
    void findByIdReturnsSeededUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.email", is("ilcr.dev@gov.bc.ca")));
    }

    @Test
    void findByIdReturnsNotFoundForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound());
    }
}
