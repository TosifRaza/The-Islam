import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import HealthBadge from '../components/HealthBadge'

interface NavItem {
  to: string
  label: string
  /** shown when ANY listed permission is held (empty = all authenticated admins) */
  anyPermission?: string[]
  /** shown when ANY listed role is held */
  anyRole?: string[]
}

const NAV: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/review-queue', label: 'Review queue', anyRole: ['super_admin', 'admin', 'content_manager', 'scholar_reviewer'] },
  { to: '/books', label: 'Books', anyPermission: ['content.edit'] },
  { to: '/content', label: 'Content', anyPermission: ['content.edit'] },
  { to: '/subjects', label: 'Subjects', anyPermission: ['academy.manage'] },
  { to: '/academy', label: 'Academy', anyPermission: ['academy.manage'] },
  { to: '/assessments', label: 'Assessments', anyPermission: ['assessment.manage', 'academy.manage'] },
  { to: '/research', label: 'Research', anyPermission: ['content.edit'] },
  { to: '/scholars', label: 'Scholars', anyPermission: ['scholars.manage'] },
  { to: '/files', label: 'Files & uploads', anyPermission: ['files.upload'] },
  { to: '/fatwas', label: 'Fatwas', anyPermission: ['fatwa.create', 'fatwa.publish', 'fatwa.review.approve'] },
  { to: '/users', label: 'Users', anyPermission: ['users.view', 'users.manage'] },
  { to: '/audit-logs', label: 'Audit log', anyPermission: ['audit.view'] },
]

function SidebarLink({ to, label, active }: { to: string; label: string; active: boolean }) {
  return (
    <NavLink
      to={to}
      className={`block rounded px-3 py-2 text-sm transition ${
        active ? 'bg-gold-islamic font-medium text-emerald-deep' : 'text-emerald-50/90 hover:bg-white/10'
      }`}
    >
      {label}
    </NavLink>
  )
}

export default function AdminLayout() {
  const { me, logout } = useAuth()
  const { hasPermission, hasAnyRole } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const visible = NAV.filter(
    (n) =>
      (!n.anyPermission || n.anyPermission.some((p) => hasPermission(p))) &&
      (!n.anyRole || n.anyRole.some((r) => hasAnyRole(r))),
  )

  return (
    <div className="min-h-screen bg-ivory-warm">
      {/* top bar */}
      <header className="sticky top-0 z-40 flex items-center justify-between bg-emerald-deep px-4 py-3 lg:px-6">
        <div className="flex items-center gap-3">
          <Link to="/dashboard" className="text-lg font-semibold text-white">
            The Islam
          </Link>
          <span className="hidden text-xs text-emerald-100/70 md:inline">
            Admin Portal
          </span>
        </div>
        <div className="flex items-center gap-3">
          <div className="hidden md:block">
            <HealthBadge />
          </div>
          <div className="relative" ref={menuRef}>
            <button
              className="flex items-center gap-2 rounded-full bg-white/10 py-1 pl-1 pr-3 text-sm text-white hover:bg-white/20"
              onClick={() => setMenuOpen((v) => !v)}
              aria-haspopup="menu"
              aria-expanded={menuOpen}
            >
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gold-islamic font-semibold text-emerald-deep">
                {(me?.displayName || 'A').slice(0, 1).toUpperCase()}
              </span>
              <span className="hidden max-w-[140px] truncate sm:inline">{me?.displayName || 'account'}</span>
            </button>
            {menuOpen && (
              <div className="absolute right-0 mt-2 w-64 rounded-lg bg-white p-2 text-sm shadow-xl" role="menu">
                <div className="border-b border-gray-100 px-3 py-2">
                  <p className="font-medium text-gray-900">{me?.displayName}</p>
                  <p className="truncate text-xs text-gray-500">{me?.email}</p>
                  <p className="mt-1 text-xs text-gray-500">Roles: {me?.roles.join(', ')}</p>
                </div>
                <Link
                  to="/profile"
                  className="block rounded px-3 py-2 text-gray-700 hover:bg-gray-100"
                  onClick={() => setMenuOpen(false)}
                >
                  Profile &amp; sessions
                </Link>
                <button
                  className="block w-full rounded px-3 py-2 text-left text-red-700 hover:bg-red-50"
                  onClick={async () => {
                    setMenuOpen(false)
                    await logout()
                    navigate('/login')
                  }}
                >
                  Sign out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <div className="flex">
        {/* sidebar */}
        <aside className="sticky top-[57px] hidden h-[calc(100vh-57px)] w-56 shrink-0 flex-col gap-1 overflow-y-auto bg-emerald-deep p-3 md:flex">
          {visible.map((n) => (
            <SidebarLink key={n.to} to={n.to} label={n.label} active={location.pathname.startsWith(n.to)} />
          ))}
          <div className="mt-auto px-3 pt-4 text-[10px] leading-relaxed text-emerald-100/50">
            M2 Admin CMS · development build
            <br />
            Content import gated at OD-05/G-3
          </div>
        </aside>

        {/* mobile nav */}
        <div className="flex w-full flex-col md:hidden">
          <nav className="flex flex-wrap gap-1 border-b border-emerald-deep/20 bg-emerald-deep px-2 pb-2">
            {visible.map((n) => (
              <Link
                key={n.to}
                to={n.to}
                className={`rounded px-2.5 py-1.5 text-xs ${
                  location.pathname.startsWith(n.to) ? 'bg-gold-islamic font-medium text-emerald-deep' : 'text-emerald-50/90'
                }`}
              >
                {n.label}
              </Link>
            ))}
          </nav>
        </div>
      </div>

      <main className="mx-auto max-w-6xl flex-1 p-4 lg:p-6 md:ml-0">
        <Outlet />
      </main>
    </div>
  )
}
