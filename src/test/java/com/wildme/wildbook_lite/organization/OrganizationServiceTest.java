package com.wildme.wildbook_lite.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wildme.wildbook_lite.auth.AppPrincipal;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.organization.dto.AddOrgMemberRequest;
import com.wildme.wildbook_lite.organization.dto.CreateOrganizationRequest;
import com.wildme.wildbook_lite.organization.dto.UpdateOrganizationRequest;
import com.wildme.wildbook_lite.project.Project;
import com.wildme.wildbook_lite.project.ProjectRepository;

/**
 * Unit tests for {@link OrganizationService} — multi-tenancy invariants.
 *
 * Focus areas:
 *  - Slug uniqueness + derivation
 *  - Last-owner protection (the GitHub-org-style guard)
 *  - Delete refusal when projects still exist
 *  - Member upsert vs insert
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock OrganizationRepository orgRepo;
    @Mock OrganizationMemberRepository memberRepo;
    @Mock ProjectRepository projectRepo;

    @InjectMocks
    OrganizationService svc;

    private static final Long CURRENT_USER_ID = 42L;
    private static final Long ORG_ID = 7L;

    @BeforeEach
    void login() {
        AppPrincipal principal = new AppPrincipal(
            CURRENT_USER_ID, "alice", "ignored", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    // ---------- create ----------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path: org saved + caller bootstrapped as OWNER")
        void happyPath() {
            when(orgRepo.findBySlugIgnoreCase("wild-me-pacific")).thenReturn(Optional.empty());
            when(orgRepo.save(any(Organization.class))).thenAnswer(inv -> {
                Organization o = inv.getArgument(0);
                o.setId(ORG_ID);
                return o;
            });

            Organization out = svc.create(new CreateOrganizationRequest(
                "Wild Me Pacific", "wild-me-pacific", "Pacific research arm"));

            assertThat(out.getId()).isEqualTo(ORG_ID);
            assertThat(out.getName()).isEqualTo("Wild Me Pacific");
            assertThat(out.getOwnerUserId()).isEqualTo(CURRENT_USER_ID);
            // OWNER bootstrap row saved
            ArgumentCaptor<OrganizationMember> cap = ArgumentCaptor.forClass(OrganizationMember.class);
            verify(memberRepo).save(cap.capture());
            assertThat(cap.getValue().getRole()).isEqualTo(OrgRole.OWNER);
            assertThat(cap.getValue().getUserId()).isEqualTo(CURRENT_USER_ID);
        }

        @Test
        @DisplayName("slug derived from name when blank")
        void derivesSlug() {
            when(orgRepo.findBySlugIgnoreCase("hawaii-humpbacks-2026")).thenReturn(Optional.empty());
            when(orgRepo.save(any(Organization.class))).thenAnswer(inv -> {
                Organization o = inv.getArgument(0);
                o.setId(ORG_ID);
                return o;
            });

            Organization out = svc.create(new CreateOrganizationRequest(
                "Hawaii Humpbacks 2026", null, null));

            assertThat(out.getSlug()).isEqualTo("hawaii-humpbacks-2026");
        }

        @Test
        @DisplayName("slug collision is rejected with 400")
        void slugCollision() {
            Organization existing = new Organization("Other", "wild-me-pacific", null, 99L);
            when(orgRepo.findBySlugIgnoreCase("wild-me-pacific")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> svc.create(new CreateOrganizationRequest(
                    "Wild Me Pacific", "wild-me-pacific", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("slug already");

            verify(orgRepo, never()).save(any());
            verify(memberRepo, never()).save(any());
        }
    }

    // ---------- delete ----------

    @Nested
    @DisplayName("deleteById")
    class Delete {

        @Test
        @DisplayName("refuses delete when projects still exist")
        void refusesWithProjects() {
            Organization org = orgFixture();
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(projectRepo.findByOrganizationId(ORG_ID)).thenReturn(
                List.of(new Project("p1", null, 1L)));

            assertThatThrownBy(() -> svc.deleteById(ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("still owns");

            verify(orgRepo, never()).delete(any());
            verify(memberRepo, never()).deleteAll(any());
        }

        @Test
        @DisplayName("happy path: members wiped first, then org")
        void happyDelete() {
            Organization org = orgFixture();
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(projectRepo.findByOrganizationId(ORG_ID)).thenReturn(List.of());
            List<OrganizationMember> members = List.of(
                new OrganizationMember(ORG_ID, 1L, OrgRole.OWNER),
                new OrganizationMember(ORG_ID, 2L, OrgRole.MEMBER));
            when(memberRepo.findByOrgId(ORG_ID)).thenReturn(members);

            svc.deleteById(ORG_ID);

            verify(memberRepo, times(1)).deleteAll(members);
            verify(orgRepo, times(1)).delete(org);
        }
    }

    // ---------- member ops ----------

    @Nested
    @DisplayName("addOrUpdateMember")
    class AddOrUpdate {

        @Test
        @DisplayName("insert when user not yet a member")
        void insertsNewRow() {
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(orgFixture()));
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.empty());
            when(memberRepo.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

            OrganizationMember out = svc.addOrUpdateMember(ORG_ID,
                new AddOrgMemberRequest(5L, OrgRole.MEMBER));

            assertThat(out.getUserId()).isEqualTo(5L);
            assertThat(out.getRole()).isEqualTo(OrgRole.MEMBER);
        }

        @Test
        @DisplayName("upsert: existing member's role is overwritten")
        void overwritesRole() {
            OrganizationMember existing = new OrganizationMember(ORG_ID, 5L, OrgRole.MEMBER);
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(orgFixture()));
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.of(existing));
            when(memberRepo.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

            OrganizationMember out = svc.addOrUpdateMember(ORG_ID,
                new AddOrgMemberRequest(5L, OrgRole.OWNER));

            assertThat(out.getRole()).isEqualTo(OrgRole.OWNER);
        }

        @Test
        @DisplayName("refuses to demote the last OWNER")
        void lastOwnerCannotBeDemoted() {
            OrganizationMember solo = new OrganizationMember(ORG_ID, 5L, OrgRole.OWNER);
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(orgFixture()));
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.of(solo));
            when(memberRepo.countByOrgIdAndRole(ORG_ID, OrgRole.OWNER)).thenReturn(1L);

            assertThatThrownBy(() -> svc.addOrUpdateMember(ORG_ID,
                    new AddOrgMemberRequest(5L, OrgRole.MEMBER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last OWNER");

            verify(memberRepo, never()).save(any());
        }

        @Test
        @DisplayName("demote allowed when other OWNERs still remain")
        void demoteWhenOtherOwners() {
            OrganizationMember target = new OrganizationMember(ORG_ID, 5L, OrgRole.OWNER);
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(orgFixture()));
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.of(target));
            when(memberRepo.countByOrgIdAndRole(ORG_ID, OrgRole.OWNER)).thenReturn(2L);
            when(memberRepo.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

            OrganizationMember out = svc.addOrUpdateMember(ORG_ID,
                new AddOrgMemberRequest(5L, OrgRole.MEMBER));

            assertThat(out.getRole()).isEqualTo(OrgRole.MEMBER);
        }
    }

    @Nested
    @DisplayName("removeMember")
    class Remove {

        @Test
        @DisplayName("happy path: MEMBER row removed")
        void removeMember() {
            OrganizationMember m = new OrganizationMember(ORG_ID, 5L, OrgRole.MEMBER);
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.of(m));

            svc.removeMember(ORG_ID, 5L);
            verify(memberRepo).delete(m);
        }

        @Test
        @DisplayName("not a member → NotFoundException")
        void notFound() {
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.removeMember(ORG_ID, 5L))
                .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("refuses to remove the last OWNER")
        void lastOwnerCannotLeave() {
            OrganizationMember solo = new OrganizationMember(ORG_ID, 5L, OrgRole.OWNER);
            when(memberRepo.findByOrgIdAndUserId(ORG_ID, 5L)).thenReturn(Optional.of(solo));
            when(memberRepo.countByOrgIdAndRole(ORG_ID, OrgRole.OWNER)).thenReturn(1L);

            assertThatThrownBy(() -> svc.removeMember(ORG_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last OWNER");

            verify(memberRepo, never()).delete(any(OrganizationMember.class));
        }
    }

    // ---------- update ----------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("partial update: only non-null fields applied")
        void partialUpdate() {
            Organization org = orgFixture();
            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(orgRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

            Organization out = svc.update(ORG_ID,
                new UpdateOrganizationRequest("New Name", null, null));

            assertThat(out.getName()).isEqualTo("New Name");
            // slug + description unchanged
            assertThat(out.getSlug()).isEqualTo(org.getSlug());
        }

        @Test
        @DisplayName("slug collision with a different org → rejected")
        void slugCollision() {
            Organization mine = orgFixture();      // id 7, slug "wild-me"
            Organization other = new Organization("Other", "taken-slug", null, 99L);
            other.setId(999L);

            when(orgRepo.findById(ORG_ID)).thenReturn(Optional.of(mine));
            when(orgRepo.findBySlugIgnoreCase("taken-slug")).thenReturn(Optional.of(other));

            assertThatThrownBy(() -> svc.update(ORG_ID,
                    new UpdateOrganizationRequest(null, "taken-slug", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("slug collision");
        }
    }

    // ---------- slugify ----------

    @Nested
    @DisplayName("slugify")
    class Slugify {

        @Test
        @DisplayName("strips punctuation + collapses spaces + lowercases")
        void basic() {
            assertThat(OrganizationService.slugify("Wild Me, Pacific!"))
                .isEqualTo("wild-me-pacific");
        }

        @Test
        @DisplayName("non-Latin characters are stripped, falls back to 'org' if empty")
        void unicodeFallback() {
            assertThat(OrganizationService.slugify("座头鲸")).isEqualTo("org");
        }
    }

    // ---------- helpers ----------

    private Organization orgFixture() {
        Organization o = new Organization("Wild Me Pacific", "wild-me", "desc", 1L);
        o.setId(ORG_ID);
        return o;
    }
}
