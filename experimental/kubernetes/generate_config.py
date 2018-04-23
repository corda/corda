from argparse import ArgumentParser
import glob
import os

from jinja2 import Environment, FileSystemLoader


env = Environment(loader=FileSystemLoader('templates'))
service_template = env.get_template('service.yml.j2')
statefulset_template = env.get_template('statefulset.yml.j2')
loadgenerator_template = env.get_template('load-generator.yml')
distribute_cordapp_tmpl = env.get_template('distribute-healthcheck-cordapp.yml')


def main():
    p = ArgumentParser()
    p.add_argument("--namespace", "-n", help="namespace where to deploy", required=True)
    p.add_argument("--storage-class", "-s", default="standard", help="storage-class for volume claims [default=default]")
    args = p.parse_args()
    namespace = args.namespace
    storage_class = args.storage_class

    load_gen_image = open('built/load_generator.txt').read().strip()
    cordapps_image = open('built/cordapps.txt').read().strip()

    for l in open('built/node-images.txt'):
        version, image = l.split()
        p = os.path.join('config', version)
        makedirs(p)
        version = version.replace(".", "-").lower()
        open(os.path.join(p, 'service.yml'), 'w').write(service_template.render(version=version))
        open(os.path.join(p, 'statefulset.yml'), 'w').write(statefulset_template.render(version=version, image=image, namespace=namespace))

    # Emit load generator config.
    p = os.path.join('config', 'load-generator')
    makedirs(p)
    # The load generator will hit the node with the last version in the input.
    # Since the load generator discovers all nodes via network map snapshot, it
    # is not necessary to redeploy the load generator if another load generator
    # is already running.
    open(os.path.join(p, 'load-generator.yml'), 'w').write(loadgenerator_template.render(version=version, namespace=namespace, image=load_gen_image))
    open(os.path.join(p, 'distribute-cordapp-job.yml'), 'w').write(distribute_cordapp_tmpl.render(image=cordapps_image))

    # Genrerate persistent volume claims.
    env = Environment(loader=FileSystemLoader('templates/persistent-volume-claims'))
    p = 'config/persistent-volume-claims'
    makedirs(p)
    for templ in glob.glob('templates/persistent-volume-claims/*'):
        b = os.path.basename(templ)
        t = env.get_template(b)
        open(p +'/'+ b, 'w').write(t.render(storage_class=storage_class))

    # Prep for doorman and notary.
    services_env = Environment(loader=FileSystemLoader('templates/services'))
    pods_env = Environment(loader=FileSystemLoader('templates/pods'))

    # Generate doorman.
    doorman_name, doorman_image = open('built/doorman.txt').read().split()
    templ = services_env.get_template('doorman.yml')
    makedirs('config/doorman')
    open('config/doorman/service.yml', 'w').write(templ.render(image=doorman_image, name=doorman_name))
    templ = pods_env.get_template('doorman.yml')
    open('config/doorman/pod.yml', 'w').write(templ.render(image=doorman_image, name=doorman_name))
    templ = pods_env.get_template('doorman-init.yml')
    open('config/doorman/pod-init.yml', 'w').write(templ.render(image=doorman_image, name=doorman_name))

    # Generate notary.
    notary_name, notary_image = open('built/notary.txt').read().split()
    templ = services_env.get_template('notary.yml')
    makedirs('config/notary')
    open('config/notary/service.yml', 'w').write(templ.render(image=notary_image, name=notary_name))
    templ = pods_env.get_template('notary.yml')
    open('config/notary/pod.yml', 'w').write(templ.render(image=notary_image, name=notary_name, namespace=namespace))
    templ = pods_env.get_template('notary-init.yml')
    open('config/notary/pod-init.yml', 'w').write(templ.render(image=notary_image, name=notary_name, namespace=namespace))

    # Template the HA nodes.
    try:
        makedirs('config/ha')
        _, haimage = open('built/ha-node-image.txt').read().split()
        templ = pods_env.get_template('ha-node.yml')
        open('config/ha/node-a.yml', 'w').write(templ.render(image=haimage, name='hanode-a'))
        open('config/ha/node-b.yml', 'w').write(templ.render(image=haimage, name='hanode-b'))
        templ = services_env.get_template('ha.yml')
        open('config/ha/service.yml', 'w').write(templ.render())
        templ = services_env.get_template('db.yml')
        open('config/ha/db-service.yml', 'w').write(templ.render())
        templ = pods_env.get_template('db.yml')
        open('config/ha/db-pod.yml', 'w').write(templ.render())

        # Hot warm
        _, hwimage = open('built/hot-warm-image.txt').read().split()
        templ = pods_env.get_template('hot-warm.yml')
        open('config/ha/hot-warm.yml', 'w').write(templ.render(image=hwimage, name='hot-warm'))

        templ = services_env.get_template('zk.yml')
        open('config/ha/zk-service.yml', 'w').write(templ.render())
        templ = pods_env.get_template('zk.yml')
        open('config/ha/zk-pod.yml', 'w').write(templ.render())
    except IOError:
        pass # Assuming the HA node images are not built.

def makedirs(p):
    try:
        os.makedirs(p)
    except Exception: # assuming file exists
        pass


if __name__ == '__main__':
    main()


