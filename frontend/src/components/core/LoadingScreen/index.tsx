import type { FC } from 'react'
import { Loading } from '@carbon/react'
import './index.scss'

type LoadingScreenProps = {
  label?: string
}

const LoadingScreen: FC<LoadingScreenProps> = ({ label = 'Loading content' }) => (
  <div className="loading-screen" role="status" aria-label={label}>
    <Loading small withOverlay={false} />
  </div>
)

export default LoadingScreen
