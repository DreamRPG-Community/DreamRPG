from pathlib import Path
import base64
import gzip
import tempfile
import unittest
from unittest.mock import patch

import migrate_item_exchanges as migration


class MigrationTest(unittest.TestCase):
    def test_tasks_are_separated_without_changing_any_exchange(self):
        npc = '67f105db-0314-3dbc-bd98-8309e0830a74'
        stages = {'version': 1, 'stages': {'origin': {'name': '起源', 'books': ['mainline_book_origin']}}}
        shops = [{'npc': npc, 'name': '赛利亚', 'recipes': [
            {'requires': [{'item': 'mainline_book_origin', 'amount': 1}, {'item': 'chicken', 'amount': 16}],
             'reward': {'item': 'mainline_book_next', 'amount': 1}},
            {'requires': [{'item': 'raw_chicken', 'amount': 1}, {'item': 'copper', 'amount': 1}],
             'reward': {'item': 'chicken', 'amount': 1}}]}]
        chapters, exchanges, report = migration.convert(stages, shops)
        self.assertEqual(list(chapters['stages']['origin']['tasks']), ['origin'])
        self.assertEqual(report['exchanges'], 1)
        self.assertEqual(report['source_offers'], 2)
        self.assertTrue(report['all_costs_and_rewards_preserved'])
        self.assertEqual(exchanges['exchanges']['village_cooking']['npc'], npc)
        with self.assertRaisesRegex(ValueError, 'version 1'):
            migration.convert(chapters, shops)

    def test_library_identity_and_quantity_are_required(self):
        name = b'copper'
        payload = b'\x0a\x00\x00\x08\x00\x07item-id' + len(name).to_bytes(2, 'big') + name + b'\x00'
        stack = {'amount': 16, 'meta': {'internal': base64.b64encode(gzip.compress(payload)).decode()}}
        self.assertEqual(migration.reference(stack, {'copper': {}}), {'item': 'copper', 'amount': 16})
        with self.assertRaisesRegex(ValueError, 'Unknown RPG item'):
            migration.reference(stack, {})
        stack['amount'] = 1.5
        with self.assertRaisesRegex(ValueError, 'amount'):
            migration.reference(stack, {'copper': {}})

    def test_partial_write_failure_restores_existing_files_and_removes_new_ones(self):
        with tempfile.TemporaryDirectory() as directory:
            original = Path(directory) / 'stages.yml'
            created = Path(directory) / 'exchanges.yml'
            last = Path(directory) / 'npcs.yml'
            original.write_bytes(b'original stages')
            last.write_bytes(b'original NPC bindings')
            publish = migration.atomic_write
            calls = 0

            def fail_once(path, content):
                nonlocal calls
                calls += 1
                if calls == 3:
                    raise OSError('acceptance write failure')
                publish(path, content)

            with patch.object(migration, 'atomic_write', side_effect=fail_once):
                with self.assertRaises(OSError):
                    migration.apply({original: b'new stages', created: b'new exchanges', last: b'new bindings'})
            self.assertEqual(original.read_bytes(), b'original stages')
            self.assertEqual(last.read_bytes(), b'original NPC bindings')
            self.assertFalse(created.exists())


if __name__ == '__main__':
    unittest.main()
