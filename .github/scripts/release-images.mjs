import { execFileSync } from 'node:child_process'
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const run = (command, args) => execFileSync(command, args, { encoding: 'utf8' }).trim()
const output = (values) => {
  for (const [key, value] of Object.entries(values)) {
    appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`)
  }
}

export function validateManifest(manifest, expected) {
  for (const key of ['repository', 'sha', 'run_id', 'run_attempt']) {
    if (!expected[key] || manifest[key] !== expected[key]) {
      throw new Error(`Release manifest ${key} does not match the successful TEST run`)
    }
  }
  for (const component of ['backend', 'frontend']) {
    const image = manifest[`${component}_image`]
    const prefix = `ghcr.io/${expected.repository.toLowerCase()}/${component}@`
    if (typeof image !== 'string' || !image.startsWith(prefix) ||
        !/^sha256:[a-f0-9]{64}$/.test(image.slice(prefix.length))) {
      throw new Error(`Release manifest must pin the repository's ${component} image by digest`)
    }
  }
  return { backend_image: manifest.backend_image, frontend_image: manifest.frontend_image }
}

function main() {
  const env = process.env
  const identity = {
    repository: env.GITHUB_REPOSITORY,
    sha: env.GITHUB_SHA,
    run_id: env.GITHUB_RUN_ID,
    run_attempt: env.GITHUB_RUN_ATTEMPT,
  }
  switch (process.argv[2]) {
    case 'capture': {
      if (!/^[1-9][0-9]*$/.test(env.PR_NUMBER ?? '')) throw new Error('Missing merged PR number')
      const images = {}
      for (const component of ['backend', 'frontend']) {
        const repository = `ghcr.io/${env.GITHUB_REPOSITORY.toLowerCase()}/${component}`
        const digest = run('docker', ['buildx', 'imagetools', 'inspect',
          `${repository}:${env.PR_NUMBER}`, '--format', '{{json .Manifest}}'])
        images[`${component}_image`] = `${repository}@${JSON.parse(digest).digest}`
      }
      output({ ...validateManifest({ ...identity, ...images }, identity), capture_attempt: env.GITHUB_RUN_ATTEMPT })
      break
    }
    case 'record': {
      // Partial reruns can test a newer deployment while reusing this run's old image outputs.
      if (env.CAPTURE_ATTEMPT !== env.GITHUB_RUN_ATTEMPT) {
        throw new Error('Rerun all Merge jobs so this attempt redeploys and tests its own images')
      }
      const manifest = { ...identity, backend_image: env.BACKEND_IMAGE, frontend_image: env.FRONTEND_IMAGE }
      validateManifest(manifest, identity)
      writeFileSync(process.argv[3], `${JSON.stringify(manifest, null, 2)}\n`)
      break
    }
    case 'resolve': {
      if (env.GITHUB_REF !== 'refs/heads/main') throw new Error('Run Release PROD from the main branch')
      const tag = env.RELEASE_TAG ?? ''
      // Restrict the input before it reaches git or any workflow output.
      if (!/^[A-Za-z0-9][A-Za-z0-9._/-]*$/.test(tag)) throw new Error('Invalid release tag')
      run('git', ['check-ref-format', `refs/tags/${tag}`])
      const sha = run('git', ['rev-parse', '--verify', `refs/tags/${tag}^{commit}`])
      run('git', ['merge-base', '--is-ancestor', sha, env.GITHUB_SHA])
      const response = JSON.parse(run('gh', ['api',
        `repos/${env.GITHUB_REPOSITORY}/actions/workflows/merge.yml/runs?event=push&branch=main&head_sha=${sha}&status=success&per_page=1`]))
      const testRun = response.workflow_runs?.[0]
      if (!testRun || testRun.head_sha !== sha || testRun.head_branch !== 'main' ||
          testRun.event !== 'push' || testRun.conclusion !== 'success' ||
          !Number.isSafeInteger(testRun.id) || !Number.isSafeInteger(testRun.run_attempt)) {
        throw new Error('The tagged commit has no successful Merge workflow with TEST acceptance')
      }
      output({ sha, run_id: testRun.id, run_attempt: testRun.run_attempt })
      break
    }
    case 'validate': {
      const manifest = JSON.parse(readFileSync(process.argv[3], 'utf8'))
      const images = validateManifest(manifest, {
        repository: env.GITHUB_REPOSITORY,
        sha: env.RELEASE_SHA,
        run_id: env.TEST_RUN_ID,
        run_attempt: env.TEST_RUN_ATTEMPT,
      })
      output(images)
      appendFileSync(env.GITHUB_STEP_SUMMARY,
        `Release commit: ${manifest.sha}\n\nTEST run: https://github.com/${manifest.repository}/actions/runs/${manifest.run_id}\n\n` +
        `Backend: \`${images.backend_image}\`\n\nFrontend: \`${images.frontend_image}\`\n`)
      break
    }
    default:
      throw new Error('Expected capture, record, resolve, or validate')
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main()
  } catch (error) {
    console.error(error.message)
    process.exitCode = 1
  }
}
