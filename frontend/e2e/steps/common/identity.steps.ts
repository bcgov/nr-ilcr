import { Given } from '../fixtures';
import { seedMockUser } from '../../pages/common/mockUser';

/**
 * Who the scenario acts as — cross-domain, no domain vocabulary.
 *
 * The global `page` fixture seeds the SUBMITTER for every scenario (steps/fixtures/global.ts), which is
 * right for every schedule. The administration surfaces are ADMIN-only — their nav group is not even
 * rendered for a submitter — so a scenario on one states the administrator as a precondition rather
 * than inheriting it. Init scripts run in the order they were added, so this later seed wins; it must
 * run BEFORE the step that first opens the app, because an init script applies only to documents opened
 * after it is added.
 */
Given('I am acting as the Ministry Administrator', async ({ page }) => {
  await seedMockUser(page, 'admin');
});
