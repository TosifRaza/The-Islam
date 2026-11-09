import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ToastProvider } from './components/ui'
import AdminLayout from './layouts/AdminLayout'
import { RequireAuth, RequirePermission } from './routes/ProtectedRoute'
import LoginPage from './features/auth/LoginPage'
import DashboardPage from './features/dashboard/DashboardPage'
import UsersPage from './features/users/UsersPage'
import UserDetailPage from './features/users/UserDetailPage'
import SubjectsPage from './features/subjects/SubjectsPage'
import BooksPage from './features/books/BooksPage'
import BookFormPage from './features/books/BookFormPage'
import BookDetailPage from './features/books/BookDetailPage'
import ContentPage from './features/content/ContentPage'
import FilesPage from './features/uploads/FilesPage'
import ReviewQueuePage from './features/review/ReviewQueuePage'
import FatwasPage from './features/review/FatwasPage'
import { FatwaDetailPage, FatwaFormPage } from './features/review/FatwasPage'
import AcademyPage from './features/academy/AcademyPage'
import AssessmentsPage from './features/assessment/AssessmentsPage'
import ResearchPage from './features/research/ResearchPage'
import AssistantAdminPage from './features/assistant/AssistantAdminPage'
import ScholarsPage from './features/scholars/ScholarsPage'
import AuditPage from './features/audit/AuditPage'
import ProfilePage from './features/profile/ProfilePage'

/**
 * M2 Admin CMS routing. Guards mirror the backend RBAC authority (server
 * derives role/permission per request; hiding UI is never the boundary).
 */
export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<AdminLayout />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route
                path="/users"
                element={
                  <RequirePermission anyPermission={['users.view', 'users.manage']}>
                    <UsersPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/users/:userId"
                element={
                  <RequirePermission anyPermission={['users.view', 'users.manage']}>
                    <UserDetailPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/subjects"
                element={
                  <RequirePermission anyPermission={['academy.manage']}>
                    <SubjectsPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/academy"
                element={
                  <RequirePermission anyPermission={['academy.manage']}>
                    <AcademyPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/assessments"
                element={
                  <RequirePermission anyPermission={['assessment.manage', 'academy.manage']}>
                    <AssessmentsPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/research"
                element={
                  <RequirePermission anyPermission={['content.edit']}>
                    <ResearchPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/assistant-ai"
                element={<AssistantAdminPage />}
              />
              <Route
                path="/scholars"
                element={
                  <RequirePermission anyPermission={['scholars.manage']}>
                    <ScholarsPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/books"
                element={
                  <RequirePermission anyPermission={['content.edit']}>
                    <BooksPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/books/new"
                element={
                  <RequirePermission anyPermission={['content.edit']}>
                    <BookFormPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/books/:bookId"
                element={
                  <RequirePermission anyPermission={['content.edit']}>
                    <BookDetailPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/books/:bookId/edit"
                element={
                  <RequirePermission anyPermission={['content.edit']}>
                    <BookFormPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/content"
                element={
                  <RequirePermission anyPermission={['content.edit']}>
                    <ContentPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/files"
                element={
                  <RequirePermission anyPermission={['files.upload']}>
                    <FilesPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/review-queue"
                element={
                  <RequirePermission anyRole={['super_admin', 'admin', 'content_manager', 'scholar_reviewer']}>
                    <ReviewQueuePage />
                  </RequirePermission>
                }
              />
              <Route
                path="/fatwas"
                element={
                  <RequirePermission
                    anyPermission={['fatwa.create', 'fatwa.publish', 'fatwa.review.approve']}
                  >
                    <FatwasPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/fatwas/new"
                element={
                  <RequirePermission anyPermission={['fatwa.create']}>
                    <FatwaFormPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/fatwas/:fatwaId"
                element={
                  <RequirePermission
                    anyPermission={['fatwa.create', 'fatwa.publish', 'fatwa.review.approve']}
                  >
                    <FatwaDetailPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/fatwas/:fatwaId/edit"
                element={
                  <RequirePermission anyPermission={['fatwa.create']}>
                    <FatwaFormPage />
                  </RequirePermission>
                }
              />
              <Route
                path="/audit-logs"
                element={
                  <RequirePermission anyPermission={['audit.view']}>
                    <AuditPage />
                  </RequirePermission>
                }
              />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Route>
          </Route>
        </Routes>
      </ToastProvider>
    </AuthProvider>
  )
}
