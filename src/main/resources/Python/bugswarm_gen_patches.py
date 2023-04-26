from collections import defaultdict
import itertools
import json
import logging
import os
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, wait
from typing import TypeVar, Iterable, Iterator

from bugswarm.common.credentials import DATABASE_PIPELINE_TOKEN
from bugswarm.common.rest_api.database_api import DatabaseAPI

# OUT_DIR = os.path.expanduser('~/dfs_repos/GitHub_JAVA')


logging.basicConfig(force=True)
logger = logging.getLogger()
logger.setLevel(logging.INFO)


T = TypeVar('T')


def chunks(it: Iterable[T], n: int) -> Iterator[Iterator[T]]:
    """
    An iterator that yields non-overlapping chunks of size `n` from the
    iterator `it`. If `it`'s length is not evenly divisible by `n`, the
    remainder is yielded last.
    """
    it = iter(it)
    try:
        while True:
            el = next(it)
            yield itertools.chain((el,), itertools.islice(it, n - 1))
    except StopIteration:
        pass


def fake_patches(shas: 'set[tuple[str, str]]', repo_dir: str, lang_suffix: str):
    patches = []
    for failed_sha, passed_sha in shas:
        try:
            header = 'compare {} {}'.format(failed_sha, passed_sha)
            diff_hunks = subprocess.run(['git', 'diff', failed_sha + '..' + passed_sha, '--', '*.' + lang_suffix],
                                        capture_output=True, check=True, cwd=repo_dir).stdout.decode('utf-8')

            if diff_hunks:
                patches.append(header + '\n' + diff_hunks)
        except subprocess.SubprocessError:
            pass

    return patches


def pull_refs(shas: 'set[tuple[str, str]]', repo_dir: str):
    all_commits = set(el for tup in shas for el in tup)
    chunk_size = 50
    for commits in chunks(all_commits, chunk_size):
        subprocess.run(['git', 'fetch', 'origin', *commits], cwd=repo_dir, capture_output=True)

    subprocess.run(['git', 'fetch', 'origin', 'refs/pull/*/head:refs/remotes/origin/pr/*'],
                   cwd=repo_dir, capture_output=True)


def process_repo(repo: str, repo_dir: str, shas: 'set[tuple[str, str]]', lang_suffix: int):
    patch_path = repo_dir + '.patch'

    logger.info(f'{repo}: Pulling refs...')
    pull_refs(shas, repo_dir)

    logger.info(f'{repo}: Generating patches...')
    patches = fake_patches(shas, repo_dir, lang_suffix)
    logger.info(f'{repo}: Generated {len(patches)} patches from {len(shas)} build pairs.')

    logger.info(f'{repo}: Writing patch file to {patch_path}...')
    with open(patch_path, 'w') as f:
        chars_written = f.write('\n\n'.join(patches) + '\n')
    logger.info(f'{repo}: Wrote {chars_written} characters to {patch_path}.')
    logger.info(f'{repo}: Finished!')


def get_shas_from_bps(repos):
    shas = defaultdict(set)
    api = DatabaseAPI(DATABASE_PIPELINE_TOKEN)
    for repo, _ in repos:
        bps = api.filter_mined_build_pairs(json.dumps({'repo': repo, 'ci_service': 'github'}))
        shas[repo].update((bp['failed_build']['head_sha'], bp['passed_build']['head_sha']) for bp in bps)
    return shas


def get_shas_from_artifacts(artifact_file):
    shas = defaultdict(set)
    api = DatabaseAPI(DATABASE_PIPELINE_TOKEN)

    with open(artifact_file) as f:
        image_tags = f.read().splitlines()

    for image_tag in image_tags:
        artifact = api.find_artifact(image_tag).json()
        shas[artifact['repo']].add((artifact['failed_job']['trigger_sha'], artifact['passed_job']['trigger_sha']))
    return shas


def main(repo_container_dir: str, lang_suffix: str, mode: str, artifact_file=''):
    logger.info(f'Mode: {mode}')

    # (Repo name; absolute path)
    repos = []
    for dirent in os.scandir(repo_container_dir):
        if dirent.is_dir() and re.match(r'[-\w]+\.[-\w]+', dirent.name):
            repos.append((dirent.name.replace('.', '/'), os.path.abspath(dirent.path)))

    # Get failed and passed SHA pairs
    logger.info(f'Getting SHA pairs...')
    if mode == 'buildpairs':
        shas_by_repo = get_shas_from_bps(repos)
    elif mode == 'artifacts':
        shas_by_repo = get_shas_from_artifacts(artifact_file)
    else:
        raise ValueError

    with ThreadPoolExecutor(4) as executor:
        futures = [
            executor.submit(
                process_repo,
                repo,
                repo_dir,
                shas_by_repo[repo],
                lang_suffix)
            for repo, repo_dir in repos]
        results = wait(futures).done

    error_count = 0
    for result in results:
        try:
            result.result()
        except Exception:
            error_count += 1
            logger.exception('A task raised an exception.')

    if error_count:
        logger.error(f'{error_count} task(s) failed. Exiting.')
        sys.exit(1)

    logger.info(f'Processed {len(repos)} repos. Done!')


main(
    repo_container_dir=sys.argv[1],
    lang_suffix=sys.argv[2],
    mode=sys.argv[3],
    artifact_file=sys.argv[4] if len(sys.argv) >= 5 else ''
    # num_threads=int(sys.argv[3])
)
