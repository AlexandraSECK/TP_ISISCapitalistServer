/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.isis.adventureISISServer.classes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author alexa
 */
public class Services {

    /* public void updateScore(String username, World world) throws JAXBException, FileNotFoundException{
        World oldWorld = readWorldFromXml(username);
        double money=world.getMoney();
        double score= world.getScore();
        oldWorld.setScore(score);
        oldWorld.setMoney(money);
        saveWorldToXml(oldWorld,username);

    }*/
    public World getWorld(String username) throws JAXBException, FileNotFoundException {
        World world = readWorldFromXml(username);
        ProductType product = findProductById(world, 1);

        long d1 = System.currentTimeMillis();
        long d2 = world.getLastupdate();

        if (d1 == d2) {

            return world;
        }
        majWorld(world);
        world.setLastupdate(System.currentTimeMillis());

        saveWorldToXml(world, username);
        return world;
    }

    public World readWorldFromXml(String username) throws JAXBException {
        String fileName = username + "-world.xml";
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Unmarshaller u = cont.createUnmarshaller();
        World world;
        try {
            File worldFile = new File(fileName);
            world = (World) u.unmarshal(worldFile);
        } catch (Exception e) {
            InputStream input = getClass().getClassLoader().getResourceAsStream("world.xml");
            world = (World) u.unmarshal(input);
            System.out.println("nouveau monde!");
        }

        return world;
    }

    void saveWorldToXml(World world, String username) throws FileNotFoundException, JAXBException {
        String fileName = username + "-world.xml";
        OutputStream output = new FileOutputStream(fileName);
        JAXBContext cont = JAXBContext.newInstance(World.class);
        Marshaller m = cont.createMarshaller();
        m.marshal(world, output);
    }

    public Boolean updateProduct(String username, ProductType newproduct) throws JAXBException, FileNotFoundException {
// aller chercher le monde qui correspond au joueur
        World world = getWorld(username);
        //majWorld(world);
// trouver dans ce monde, le produit équivalent à celui passé
// en paramètre
        ProductType product = findProductById(world, newproduct.getId());
        if (product == null) {
            return false;
        }
// calculer la variation de quantité. Si elle est positive c'est
// que le joueur a acheté une certaine quantité de ce produit
// sinon c’est qu’il s’agit d’un lancement de production.

        int qtchange = newproduct.getQuantite() - product.getQuantite();
        if (qtchange > 0) {
            int ancienneqte = product.getQuantite();
            double prix1 = product.cout;
            double q = product.getCroissance();
            double newprix = prix1 * (1 - (Math.pow(q, qtchange)) / (1 - q));
            double argent = world.getMoney() - newprix;
            world.setMoney(argent);
            product.setQuantite(newproduct.getQuantite());
            product.setCout(Math.pow(product.getCroissance(), qtchange) * prix1);
            if (ancienneqte != 0) {
                double newRevenu = (product.getRevenu() / ancienneqte) * product.getQuantite();
                product.setRevenu(newRevenu);
            }
        } else {
// initialiser product.timeleft à product.vitesse
// pour lancer la production
            System.out.println(product.getName() + "Timeleft avant le lancement" + product.timeleft);
            product.timeleft = product.vitesse;
                        System.out.println(product.getName() + "Timeleft après lancement" + product.timeleft);

        }
        List<PallierType> listeUnlock = product.getPalliers().getPallier();
        for (PallierType p : listeUnlock) {
            if (!p.isUnlocked() && product.getQuantite() >= p.getSeuil()) {
                majPallier(p, product);
            }
        }
// sauvegarder les changements du monde
        world.setLastupdate(System.currentTimeMillis());
        saveWorldToXml(world, username);
        return true;
    }

    private ProductType findProductById(World world, int id) {
        ProductType produit = null;
        List<ProductType> p = world.getProducts().getProduct();
        for (ProductType pr : p) {
            int idp = pr.getId();
            if (idp == id) {
                produit = pr;
                return produit;
            }
        }
        return produit;
    }

    // prend en paramètre le pseudo du joueur et le manager acheté.
// renvoie false si l’action n’a pas pu être traitée
    public Boolean updateManager(String username, PallierType newmanager) throws JAXBException, FileNotFoundException {
// aller chercher le monde qui correspond au joueur*
        World world = getWorld(username);
        // trouver dans ce monde, le manager équivalent à celui passé
// en paramètre
        PallierType manager = findManagerByName(world, newmanager.getName());
        if (manager == null) {
            return false;
        }
        manager.setUnlocked(true);

        ProductType product = findProductById(world, manager.getIdcible());
        if (product == null) {
            return false;
        }
// débloquer le manager de ce produit
// soustraire de l'argent du joueur le cout du manager
// sauvegarder les changements au monde
        product.setManagerUnlocked(true);
        double argent = world.getMoney() - manager.getSeuil();
        saveWorldToXml(world, username);
        world.setMoney(argent);

        return true;
    }

    public Boolean updateUpgrade(String username, PallierType upgrade) throws JAXBException, FileNotFoundException {
        World world = getWorld(username);
        if (world.getMoney() >= upgrade.getSeuil() && !upgrade.isUnlocked()) {
            if (upgrade.getIdcible() == 0) {
                List<ProductType> listeProduits = world.getProducts().getProduct();
                for (ProductType p : listeProduits) {
                    majPallier(upgrade, p);
                }
                return true;
            } else {
                ProductType p = findProductById(world, upgrade.getIdcible());
                majPallier(upgrade, p);
                return true;
            }
        }
        return false;
    }

    private PallierType findManagerByName(World world, String name) {
        PallierType manager = null;
        List<PallierType> p = world.getManagers().getPallier();
        for (PallierType pa : p) {
            String mName = pa.getName();
            if (name.equals(mName)) {
                manager = pa;
                return manager;
            }
        }
        return manager;
    }

    public void majWorld(World world) {
        List<ProductType> ListProduit = world.getProducts().getProduct();
        long d1 = System.currentTimeMillis();
        long d2 = world.getLastupdate();
        long delta = d1- d2;
        System.out.println("temps entre les deux : "+delta);
        double angesActifs = world.getActiveangels();
        double angelBonus = world.getAngelbonus();
        //System.out.println("ancien score :" + world.getScore());
        for (ProductType pr : ListProduit) {
            if (pr.isManagerUnlocked()) {
                int tempsProduit = pr.getVitesse();
                int nbrProduit = (int) (delta / tempsProduit);
                long tpsRestant = nbrProduit - (delta % tempsProduit);
                pr.setTimeleft(tpsRestant);
                double argentGagne = (pr.getRevenu() * nbrProduit) * (1 + angesActifs * angelBonus / 100);
                world.setMoney(world.getMoney() + argentGagne);
                world.setScore(world.getScore() + argentGagne);
            } else {
                if (pr.getTimeleft() != 0 && delta > pr.getTimeleft()) {
                    world.setMoney(world.getMoney() + pr.getRevenu());
                    world.setScore(world.getScore() + pr.getRevenu());
                    pr.setTimeleft(0);
                    System.out.println("nouveau produit");
                } else {
                    if (pr.getTimeleft() != 0) {
                        long newTimeLeft = pr.getTimeleft() - delta;
                        pr.setTimeleft(newTimeLeft);
                    }
                }

            }
            //System.out.println("nouveau score :" + world.getScore());

        }
    }

    public void majPallier(PallierType p, ProductType product) {
        p.setUnlocked(true);
        if (p.getTyperatio() == TyperatioType.VITESSE) {
            double v = product.getVitesse();
            int newv = (int) (v * p.getRatio());
            product.setVitesse(newv);

        } else {
            if (p.getTyperatio() == TyperatioType.GAIN) {
                double r = product.getRevenu();
                r = r * p.getRatio();
                product.setRevenu(r);
            }
        }
    }

    public World deleteWorld(String username) throws JAXBException, FileNotFoundException {
        World world = getWorld(username);
        double activesAngels = world.getActiveangels();
        double totalAngels = world.getTotalangels();
        double angesGagnes = nbAnges(world);
        double Score = world.getScore();
        activesAngels += angesGagnes;
        totalAngels += angesGagnes;

        InputStream input = getClass().getClassLoader().getResourceAsStream("world.xml");

        JAXBContext cont = JAXBContext.newInstance(World.class);
        Unmarshaller u = cont.createUnmarshaller();
        World NewWorld = (World) u.unmarshal(input);
        NewWorld.setTotalangels(totalAngels);
        NewWorld.setActiveangels(angesGagnes);
        NewWorld.setScore(Score);
        saveWorldToXml(NewWorld, username);
        return NewWorld;
    }

    public double nbAnges(World world) {
        double angelToClaim = world.getTotalangels();
        double score = world.getScore();
        angelToClaim = Math.round(150 * Math.sqrt(score / Math.pow(10, 15))) - angelToClaim;
        return angelToClaim;
    }

    public void angelUpgrade(String username, PallierType angelUpgrade) throws JAXBException, FileNotFoundException {
        World world = getWorld(username);
        double prix = angelUpgrade.getSeuil();
        double angesActif = world.getActiveangels();
        angesActif -= prix;
        if (angelUpgrade.getTyperatio() == TyperatioType.ANGE) {
            int bonus = world.getAngelbonus();
            bonus += angelUpgrade.getRatio();
            world.setAngelbonus(bonus);
        } else {
            updateUpgrade(username, angelUpgrade);
        }
        world.setActiveangels(angesActif);

    }
}
