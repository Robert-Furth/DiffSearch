import json
import logging
import os
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, wait

from bugswarm.common.credentials import DATABASE_PIPELINE_TOKEN
from bugswarm.common.rest_api.database_api import DatabaseAPI

# OUT_DIR = os.path.expanduser('~/dfs_repos/GitHub_JAVA')


logging.basicConfig()


def fake_patches(bps: list, repo_dir: str, lang_suffix: str):
    patches = []
    for bp in bps:
        try:
            failed_sha = bp['failed_build']['head_sha']
            passed_sha = bp['passed_build']['head_sha']

            header = 'compare {} {}'.format(failed_sha, passed_sha)
            diff_hunks = subprocess.run(['git', 'diff', failed_sha + '..' + passed_sha, '--', '*.' + lang_suffix],
                                        capture_output=True, check=True, cwd=repo_dir).stdout.decode('utf-8')

            if diff_hunks:
                patches.append(header + '\n' + diff_hunks)
        except subprocess.SubprocessError:
            pass

    return patches


def process_repo(repo: str, repo_dir: str, lang_suffix: int, api: DatabaseAPI):
    patch_path = repo_dir + '.patch'

    logging.info(f'{repo}: Pulling refs...')
    subprocess.run(['git', 'fetch', 'origin', 'refs/pull/*/head:refs/remotes/origin/pr/*'],
                   cwd=repo_dir, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    logging.info(f'{repo}: Getting build pairs...')
    bps = api.filter_mined_build_pairs(json.dumps({'repo': repo, 'ci_service': 'github'}))
    logging.info(f'{repo}: Got {len(bps)} build pairs.')

    logging.info(f'{repo}: Generating patches...')
    patches = fake_patches(bps, repo_dir, lang_suffix)
    logging.info(f'{repo}: Generated {len(patches)} patches from {len(bps)} build pairs.')

    logging.info(f'{repo}: Writing patch file to {patch_path}...')
    with open(patch_path, 'w') as f:
        chars_written = f.write('\n\n'.join(patches) + '\n')
    logging.info(f'{repo}: Wrote {chars_written} characters to {patch_path}.')
    logging.info(f'{repo}: Finished!')


def main(repo_container_dir: str, lang_suffix: str, num_threads: int):
    # (Repo name; absolute path)
    repos = []
    for dirent in os.scandir(repo_container_dir):
        if dirent.is_dir() and re.match(r'[-\w]+\.[-\w]+', dirent.name):
            repos.append((dirent.name.replace('.', '/'), os.path.abspath(dirent.path)))

    api = DatabaseAPI(DATABASE_PIPELINE_TOKEN)

    with ThreadPoolExecutor(num_threads) as executor:
        futures = [executor.submit(process_repo, repo, repo_dir, lang_suffix, api) for repo, repo_dir in repos]
        wait(futures)

    logging.info(f'Processed {len(repos)} repos. Done!')


if __name__ == '__main__':
    sys.exit(main(
        repo_container_dir=sys.argv[1],
        lang_suffix=sys.argv[2],
        num_threads=int(sys.argv[3])
    ))
