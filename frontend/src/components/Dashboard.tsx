import type { FC } from 'react'
import type UserDto from '@/interfaces/UserDto'
import { useEffect, useState } from 'react'
import {
  Button,
  Column,
  DataTable,
  Grid,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  Tile,
} from '@carbon/react'
import apiService from '@/service/api-service'
import useMockAuth from '@/context/auth/useMockAuth'
import EmptySection from '@/components/core/EmptySection'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'

const headers = [
  { key: 'id', header: 'User ID' },
  { key: 'name', header: 'Name' },
  { key: 'email', header: 'Email' },
  { key: 'actions', header: '' },
]

const Dashboard: FC = () => {
  const { user } = useMockAuth()
  const [data, setData] = useState<UserDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [selectedUser, setSelectedUser] = useState<UserDto | undefined>(undefined)

  useEffect(() => {
    apiService
      .getAxiosInstance()
      .get<UserDto[]>('/v1/users')
      .then((response) => {
        const users = response.data.map(({ id, name, email }) => ({
          id,
          name,
          email,
        }))
        setData(users)
      })
      .catch(() => {
        setData([])
      })
      .finally(() => {
        setIsLoading(false)
      })
  }, [])

  const rows = data.map((item) => ({
    ...item,
    id: String(item.id),
  }))

  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__header">
        <PageTitle
          title="ILCR Workspace"
          subtitle="Interior Logging Cost Reports modernization scaffold for React and Spring Boot delivery."
        />
      </Grid>

      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={4} lg={5}>
          <Tile className="dashboard-summary">
            <h2>{user.displayName}</h2>
            <p className="dashboard-summary__meta">{user.email}</p>
            <div className="app-role-tags" aria-label="Current mock roles">
              {user.roles.map((role) => (
                <Tag key={role} type={role === 'ILCR_ADMIN' ? 'purple' : 'teal'}>
                  {role}
                </Tag>
              ))}
            </div>
          </Tile>
        </Column>

        <Column sm={4} md={4} lg={11}>
          {isLoading ? (
            <LoadingScreen label="Loading scaffold users" />
          ) : rows.length === 0 ? (
            <EmptySection
              title="No scaffold users"
              description="The local read-only users API returned no records."
            />
          ) : (
            <DataTable rows={rows} headers={headers}>
              {({
                rows: tableRows,
                headers: tableHeaders,
                getHeaderProps,
                getRowProps,
                getTableProps,
              }) => (
                <TableContainer
                  title="Scaffold Users"
                  description="Temporary API contract for local development."
                >
                  <Table {...getTableProps()}>
                    <TableHead>
                      <TableRow>
                        {tableHeaders.map((header) => {
                          const { key: headerKey, ...headerProps } = getHeaderProps({ header })
                          return (
                            <TableHeader key={headerKey} {...headerProps}>
                              {header.header}
                            </TableHeader>
                          )
                        })}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {tableRows.map((row) => {
                        const { key: rowKey, ...rowProps } = getRowProps({ row })
                        return (
                          <TableRow key={rowKey} {...rowProps}>
                            {row.cells.map((cell) => {
                              if (cell.info.header === 'actions') {
                                const rowUser = data.find((item) => String(item.id) === row.id)
                                return (
                                  <TableCell key={cell.id}>
                                    <Button
                                      kind="secondary"
                                      size="sm"
                                      onClick={() => setSelectedUser(rowUser)}
                                    >
                                      View details
                                    </Button>
                                  </TableCell>
                                )
                              }

                              return <TableCell key={cell.id}>{cell.value}</TableCell>
                            })}
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </DataTable>
          )}
        </Column>
      </Grid>

      {selectedUser && (
        <Modal
          open
          passiveModal
          modalHeading="Row Details"
          onRequestClose={() => setSelectedUser(undefined)}
        >
          <pre className="dashboard-details">{JSON.stringify(selectedUser, null, 2)}</pre>
        </Modal>
      )}
    </div>
  )
}

export default Dashboard
